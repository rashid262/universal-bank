package com.bank.transaction.service;

import com.bank.transaction.domain.Account;
import com.bank.transaction.domain.Transaction;
import com.bank.transaction.domain.User;
import com.bank.transaction.enums.Role;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExchangeRateService exchangeRateService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                accountRepository, transactionRepository, userRepository, exchangeRateService);
    }

    @Test
    void transferUsesAuthenticatedCustomerAccountAndUpdatesBothBalances() {
        Account sender = account("UB-4721", "15000.00");
        Account recipient = account("UB-9852", "10000.00");
        when(transactionRepository.existsByIdempotencyKey("transfer-1")).thenReturn(false);
        when(userRepository.findByUsername("yusuf_test")).thenReturn(Optional.of(User.builder()
                .username("yusuf_test").accountId("UB-4721").role(Role.CUSTOMER).isVerified(true).build()));
        when(userRepository.findByAccountId("UB-9852")).thenReturn(Optional.of(User.builder()
                .username("wazeh").accountId("UB-9852").role(Role.CUSTOMER).isVerified(true).build()));
        when(accountRepository.findByIdForUpdate("UB-4721")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate("UB-9852")).thenReturn(Optional.of(recipient));
        when(accountRepository.findById("UB-4721")).thenReturn(Optional.of(sender));
        when(accountRepository.findById("UB-9852")).thenReturn(Optional.of(recipient));
        when(exchangeRateService.getRate("INR", "INR")).thenReturn(new ExchangeRateService.ExchangeRate(
                "INR", "INR", BigDecimal.ONE, java.time.LocalDate.of(2026, 10, 16), "Same currency",
                java.time.Instant.parse("2026-10-16T00:00:00Z")));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = transactionService.transferMoney(
                "yusuf_test", "UB-9852", new BigDecimal("1000.00"), "QA transfer", "transfer-1",
                null, null);

        assertEquals(new BigDecimal("14000.00"), sender.getBalance());
        assertEquals(new BigDecimal("11000.00"), recipient.getBalance());
        assertEquals("UB-4721", transaction.getSourceAccountId());
        assertEquals("UB-9852", transaction.getDestinationAccountId());
        assertEquals("TRANSFER", transaction.getType());
        assertEquals(BigDecimal.ONE, transaction.getExchangeRate());
        assertEquals("INR", transaction.getSourceCurrency());
        assertEquals("INR", transaction.getDestinationCurrency());
        verify(accountRepository).findByIdForUpdate("UB-4721");
        verify(accountRepository).findByIdForUpdate("UB-9852");
    }

    @Test
    void transferRejectsNonPositiveAmountsBeforeLoadingAccounts() {
        assertThrows(IllegalArgumentException.class, () -> transactionService.transferMoney(
                "yusuf_test", "UB-9852", BigDecimal.ZERO, null, "transfer-2", null, null));

        verifyNoInteractions(userRepository, accountRepository);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transferRejectsAdminAsSender() {
        when(transactionRepository.existsByIdempotencyKey("transfer-3")).thenReturn(false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(User.builder()
                .username("admin").accountId("UB-0000").role(Role.ADMIN).isVerified(true).build()));

        assertThrows(IllegalArgumentException.class, () -> transactionService.transferMoney(
                "admin", "UB-9852", new BigDecimal("100.00"), null, "transfer-3", null, null));

        verifyNoInteractions(accountRepository);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transferRejectsPreviouslyUsedIdempotencyKey() {
        when(transactionRepository.existsByIdempotencyKey("used-transfer-key")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> transactionService.transferMoney(
                "yusuf_test", "UB-9852", new BigDecimal("100.00"), null, "used-transfer-key", null, null));

        verifyNoInteractions(userRepository, accountRepository);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void crossCurrencyTransferUsesQuotedProviderRateAndPersistsQuoteMetadata() {
        Account sender = account("UB-4721", "1000.00");
        Account recipient = new Account("UB-9852", new BigDecimal("20.00"), "USD", true);
        java.time.LocalDate rateDate = java.time.LocalDate.of(2026, 10, 16);
        java.time.Instant fetchedAt = java.time.Instant.parse("2026-10-16T10:00:00Z");
        when(transactionRepository.existsByIdempotencyKey("transfer-fx-1")).thenReturn(false);
        when(userRepository.findByUsername("usd_customer")).thenReturn(Optional.of(User.builder()
                .username("usd_customer").accountId("UB-4721").role(Role.CUSTOMER).isVerified(true).build()));
        when(userRepository.findByAccountId("UB-9852")).thenReturn(Optional.of(User.builder()
                .username("inr_customer").accountId("UB-9852").role(Role.CUSTOMER).isVerified(true).build()));
        when(accountRepository.findById("UB-4721")).thenReturn(Optional.of(sender));
        when(accountRepository.findById("UB-9852")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByIdForUpdate("UB-4721")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate("UB-9852")).thenReturn(Optional.of(recipient));
        when(exchangeRateService.getRate("INR", "USD")).thenReturn(new ExchangeRateService.ExchangeRate(
                "INR", "USD", new BigDecimal("0.01053075"), rateDate, "Frankfurter", fetchedAt));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = transactionService.transferMoney(
                "usd_customer", "UB-9852", new BigDecimal("100.00"), "FX QA", "transfer-fx-1",
                new BigDecimal("0.01053075"), rateDate);

        assertEquals(new BigDecimal("900.00"), sender.getBalance());
        assertEquals(new BigDecimal("21.05"), recipient.getBalance());
        assertEquals(new BigDecimal("100.00"), transaction.getSourceAmount());
        assertEquals(new BigDecimal("1.05"), transaction.getAmount());
        assertEquals(new BigDecimal("0.01053075"), transaction.getExchangeRate());
        assertEquals(rateDate, transaction.getExchangeRateDate());
        assertEquals("Frankfurter", transaction.getExchangeRateProvider());
        assertEquals(fetchedAt, transaction.getExchangeRateFetchedAt());
    }

    @Test
    void crossCurrencyTransferRejectsAStaleQuote() {
        Account sender = account("UB-4721", "1000.00");
        Account recipient = new Account("UB-9852", new BigDecimal("20.00"), "USD", true);
        when(transactionRepository.existsByIdempotencyKey("transfer-fx-stale")).thenReturn(false);
        when(userRepository.findByUsername("yusuf_test")).thenReturn(Optional.of(User.builder()
                .username("yusuf_test").accountId("UB-4721").role(Role.CUSTOMER).isVerified(true).build()));
        when(userRepository.findByAccountId("UB-9852")).thenReturn(Optional.of(User.builder()
                .username("wazeh").accountId("UB-9852").role(Role.CUSTOMER).isVerified(true).build()));
        when(accountRepository.findById("UB-4721")).thenReturn(Optional.of(sender));
        when(accountRepository.findById("UB-9852")).thenReturn(Optional.of(recipient));
        when(exchangeRateService.getRate("INR", "USD")).thenReturn(new ExchangeRateService.ExchangeRate(
                "INR", "USD", new BigDecimal("0.0105"), java.time.LocalDate.of(2026, 10, 16),
                "Frankfurter", java.time.Instant.parse("2026-10-16T10:00:00Z")));

        assertThrows(IllegalArgumentException.class, () -> transactionService.transferMoney(
                "yusuf_test", "UB-9852", new BigDecimal("100.00"), null, "transfer-fx-stale",
                new BigDecimal("0.0120"), java.time.LocalDate.of(2026, 10, 16)));

        assertEquals(new BigDecimal("1000.00"), sender.getBalance());
        assertEquals(new BigDecimal("20.00"), recipient.getBalance());
        verify(accountRepository, never()).findByIdForUpdate(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void crossCurrencyTransferRoundsJapaneseYenToWholeUnits() {
        Account sender = account("UB-4721", "1000.00");
        Account recipient = new Account("UB-9852", BigDecimal.ZERO, "JPY", true);
        java.time.LocalDate rateDate = java.time.LocalDate.of(2026, 10, 16);
        when(transactionRepository.existsByIdempotencyKey("transfer-jpy-1")).thenReturn(false);
        when(userRepository.findByUsername("usd_customer")).thenReturn(Optional.of(User.builder()
                .username("usd_customer").accountId("UB-4721").role(Role.CUSTOMER).isVerified(true).build()));
        when(userRepository.findByAccountId("UB-9852")).thenReturn(Optional.of(User.builder()
                .username("jpy_customer").accountId("UB-9852").role(Role.CUSTOMER).isVerified(true).build()));
        when(accountRepository.findById("UB-4721")).thenReturn(Optional.of(sender));
        when(accountRepository.findById("UB-9852")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByIdForUpdate("UB-4721")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate("UB-9852")).thenReturn(Optional.of(recipient));
        when(exchangeRateService.getRate("INR", "JPY")).thenReturn(new ExchangeRateService.ExchangeRate(
                "INR", "JPY", new BigDecimal("1.65"), rateDate, "Frankfurter",
                java.time.Instant.parse("2026-10-16T10:00:00Z")));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = transactionService.transferMoney(
                "usd_customer", "UB-9852", new BigDecimal("10.00"), "JPY QA", "transfer-jpy-1",
                new BigDecimal("1.65"), rateDate);

        assertEquals(new BigDecimal("17"), recipient.getBalance());
        assertEquals(new BigDecimal("17"), transaction.getAmount());
    }

    private Account account(String id, String balance) {
        return new Account(id, new BigDecimal(balance), "INR", true);
    }
}
