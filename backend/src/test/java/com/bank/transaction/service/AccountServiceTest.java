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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private ExchangeRateService exchangeRateService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository, transactionRepository, passwordEncoder, userRepository, mailSender,
                exchangeRateService);
    }

    @Test
    void customerCreditDebitsVaultAndRecordsBothAccounts() {
        Account vault = account("UB-0000", "1000000.00");
        Account customer = account("UB-4721", "50.00");
        when(userRepository.findByAccountId("UB-4721"))
                .thenReturn(Optional.of(User.builder().accountId("UB-4721").role(Role.CUSTOMER).build()));
        when(accountRepository.findByIdForUpdate("UB-0000")).thenReturn(Optional.of(vault));
        when(accountRepository.findByIdForUpdate("UB-4721")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsByIdempotencyKey("credit-1")).thenReturn(false);
        when(exchangeRateService.getRate("INR", "INR")).thenReturn(new ExchangeRateService.ExchangeRate(
                "INR", "INR", BigDecimal.ONE, LocalDate.of(2026, 10, 17), "Same currency", Instant.now()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = accountService.creditAccount(
                "UB-4721", new BigDecimal("100.00"), "INR", "credit-1");

        assertEquals(new BigDecimal("999900.00"), vault.getBalance());
        assertEquals(new BigDecimal("150.00"), customer.getBalance());
        assertEquals("UB-0000", transaction.getSourceAccountId());
        assertEquals("UB-4721", transaction.getDestinationAccountId());
        assertEquals("CREDIT", transaction.getType());
        verify(accountRepository).save(vault);
        verify(accountRepository).save(customer);
    }

    @Test
    void adminCreditConvertsVaultFundsToCustomerCurrencyAndPersistsRate() {
        Account vault = account("UB-0000", "1000000.00");
        Account customer = new Account("UB-9852", new BigDecimal("20.00"), "USD", true);
        LocalDate rateDate = LocalDate.of(2026, 10, 17);
        Instant fetchedAt = Instant.parse("2026-10-17T10:00:00Z");
        when(userRepository.findByAccountId("UB-9852"))
                .thenReturn(Optional.of(User.builder().accountId("UB-9852").role(Role.CUSTOMER).build()));
        when(accountRepository.findByIdForUpdate("UB-0000")).thenReturn(Optional.of(vault));
        when(accountRepository.findByIdForUpdate("UB-9852")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsByIdempotencyKey("credit-fx-1")).thenReturn(false);
        when(exchangeRateService.getRate("INR", "USD")).thenReturn(new ExchangeRateService.ExchangeRate(
                "INR", "USD", new BigDecimal("0.01053075"), rateDate, "Frankfurter", fetchedAt));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = accountService.creditAccount(
                "UB-9852", new BigDecimal("10000.00"), "INR", "credit-fx-1",
                new BigDecimal("0.01053075"), rateDate);

        assertEquals(new BigDecimal("990000.00"), vault.getBalance());
        assertEquals(new BigDecimal("125.31"), customer.getBalance());
        assertEquals(new BigDecimal("10000.00"), transaction.getSourceAmount());
        assertEquals(new BigDecimal("105.31"), transaction.getAmount());
        assertEquals("INR", transaction.getSourceCurrency());
        assertEquals("USD", transaction.getDestinationCurrency());
        assertEquals(new BigDecimal("0.01053075"), transaction.getExchangeRate());
        assertEquals(rateDate, transaction.getExchangeRateDate());
        assertEquals("Frankfurter", transaction.getExchangeRateProvider());
    }

    @Test
    void adminCreditRejectsStaleFxQuoteWithoutMovingFunds() {
        Account vault = account("UB-0000", "1000000.00");
        Account customer = new Account("UB-9852", new BigDecimal("20.00"), "USD", true);
        LocalDate rateDate = LocalDate.of(2026, 10, 17);
        when(userRepository.findByAccountId("UB-9852"))
                .thenReturn(Optional.of(User.builder().accountId("UB-9852").role(Role.CUSTOMER).build()));
        when(accountRepository.findByIdForUpdate("UB-0000")).thenReturn(Optional.of(vault));
        when(accountRepository.findByIdForUpdate("UB-9852")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsByIdempotencyKey("credit-fx-stale")).thenReturn(false);
        when(exchangeRateService.getRate("INR", "USD")).thenReturn(new ExchangeRateService.ExchangeRate(
                "INR", "USD", new BigDecimal("0.01053075"), rateDate, "Frankfurter", Instant.now()));

        assertThrows(IllegalArgumentException.class, () -> accountService.creditAccount(
                "UB-9852", new BigDecimal("10000.00"), "INR", "credit-fx-stale",
                new BigDecimal("0.011"), rateDate));

        assertEquals(new BigDecimal("1000000.00"), vault.getBalance());
        assertEquals(new BigDecimal("20.00"), customer.getBalance());
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void quoteCreditUsesVaultAndCustomerCurrencies() {
        Account vault = account("UB-0000", "1000000.00");
        Account customer = new Account("UB-9852", BigDecimal.ZERO, "USD", true);
        LocalDate rateDate = LocalDate.of(2026, 10, 17);
        when(accountRepository.findById("UB-0000")).thenReturn(Optional.of(vault));
        when(userRepository.findByAccountId("UB-9852"))
                .thenReturn(Optional.of(User.builder().accountId("UB-9852").role(Role.CUSTOMER).build()));
        when(accountRepository.findById("UB-9852")).thenReturn(Optional.of(customer));
        when(exchangeRateService.getRate("INR", "USD")).thenReturn(new ExchangeRateService.ExchangeRate(
                "INR", "USD", new BigDecimal("0.01053075"), rateDate, "Frankfurter", Instant.now()));

        var quote = accountService.quoteCredit("UB-9852", new BigDecimal("10000.00"));

        assertEquals("INR", quote.sourceCurrency());
        assertEquals("USD", quote.destinationCurrency());
        assertEquals(new BigDecimal("105.31"), quote.destinationAmount());
        assertEquals(rateDate, quote.rateDate());
    }

    @Test
    void customerCreditFailsWhenVaultHasInsufficientFunds() {
        Account vault = account("UB-0000", "99.00");
        Account customer = account("UB-4721", "50.00");
        when(userRepository.findByAccountId("UB-4721"))
                .thenReturn(Optional.of(User.builder().accountId("UB-4721").role(Role.CUSTOMER).build()));
        when(accountRepository.findByIdForUpdate("UB-0000")).thenReturn(Optional.of(vault));
        when(accountRepository.findByIdForUpdate("UB-4721")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsByIdempotencyKey("credit-2")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> accountService.creditAccount(
                "UB-4721", new BigDecimal("100.00"), "INR", "credit-2"));

        assertEquals(new BigDecimal("99.00"), vault.getBalance());
        assertEquals(new BigDecimal("50.00"), customer.getBalance());
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void vaultFundingRecordsAnOpeningBalanceTransaction() {
        Account vault = account("UB-0000", "0.00");
        when(accountRepository.findByIdForUpdate("UB-0000")).thenReturn(Optional.of(vault));
        when(transactionRepository.existsByIdempotencyKey("opening-1")).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = accountService.fundVault(new BigDecimal("10000000.00"), "opening-1");

        assertEquals(new BigDecimal("10000000.00"), vault.getBalance());
        assertEquals("OPENING_BALANCE", transaction.getSourceAccountId());
        assertEquals("UB-0000", transaction.getDestinationAccountId());
        assertEquals("VAULT_FUNDING", transaction.getType());
        verify(accountRepository).save(vault);
    }

    @Test
    void duplicateIdempotencyKeyDoesNotChangeVaultOrCustomerBalance() {
        when(transactionRepository.existsByIdempotencyKey("used-key")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> accountService.fundVault(
                new BigDecimal("100.00"), "used-key"));

        verify(accountRepository, never()).findByIdForUpdate(anyString());
        verify(accountRepository, never()).save(any(Account.class));
    }

    private Account account(String id, String balance) {
        return new Account(id, new BigDecimal(balance), "INR", true);
    }
}
