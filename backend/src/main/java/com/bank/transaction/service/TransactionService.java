package com.bank.transaction.service;

import com.bank.transaction.domain.Account;
import com.bank.transaction.domain.CurrencyCode;
import com.bank.transaction.domain.Transaction;
import com.bank.transaction.domain.User;
import com.bank.transaction.dto.TransferQuoteResponse;
import com.bank.transaction.enums.Role;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              UserRepository userRepository,
                              ExchangeRateService exchangeRateService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional(readOnly = true)
    public TransferQuoteResponse quoteTransfer(
            String authenticatedUsername, String toId, BigDecimal amount) {
        validateAmount(amount);
        User sourceUser = findVerifiedCustomer(authenticatedUsername);
        String fromId = sourceUser.getAccountId();
        if (fromId == null || fromId.equals(toId)) {
            throw new IllegalArgumentException("A transfer requires a different customer recipient account.");
        }
        User destinationUser = findVerifiedRecipient(toId);
        Account source = accountRepository.findById(fromId)
                .orElseThrow(() -> new RuntimeException("Sender account not found"));
        Account destination = accountRepository.findById(destinationUser.getAccountId())
                .orElseThrow(() -> new RuntimeException("Recipient account not found"));
        if (!source.isActive() || !destination.isActive()) {
            throw new IllegalStateException("Both customer accounts must be active.");
        }
        validateCurrencyAmount(amount, source.getCurrency());
        if (source.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in your account.");
        }

        ExchangeRateService.ExchangeRate rate = exchangeRateService.getRate(
                source.getCurrency(), destination.getCurrency());
        BigDecimal convertedAmount = convertAmount(amount, rate);
        return new TransferQuoteResponse(
                source.getCurrency(),
                destination.getCurrency(),
                amount,
                convertedAmount,
                rate.rate(),
                rate.rateDate(),
                rate.provider(),
                rate.fetchedAt());
    }

    @Transactional
    public Transaction transferMoney(
            String authenticatedUsername,
            String toId,
            BigDecimal amount,
            String note,
            String idempotencyKey,
            BigDecimal quotedExchangeRate,
            LocalDate quotedExchangeRateDate
    ) {
        validateAmount(amount);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required.");
        }
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new IllegalArgumentException("This idempotency key has already been used.");
        }

        User sourceUser = findVerifiedCustomer(authenticatedUsername);
        String fromId = sourceUser.getAccountId();
        if (fromId == null || fromId.equals(toId)) {
            throw new IllegalArgumentException("A transfer requires a different customer recipient account.");
        }
        User destinationUser = findVerifiedRecipient(toId);

        Account sourceSnapshot = accountRepository.findById(fromId)
                .orElseThrow(() -> new RuntimeException("Sender account not found"));
        Account destinationSnapshot = accountRepository.findById(destinationUser.getAccountId())
                .orElseThrow(() -> new RuntimeException("Recipient account not found"));
        if (!sourceSnapshot.isActive() || !destinationSnapshot.isActive()) {
            throw new IllegalStateException("Both customer accounts must be active.");
        }
        validateCurrencyAmount(amount, sourceSnapshot.getCurrency());

        ExchangeRateService.ExchangeRate exchangeRate = exchangeRateService.getRate(
                sourceSnapshot.getCurrency(), destinationSnapshot.getCurrency());
        if (!sourceSnapshot.getCurrency().equals(destinationSnapshot.getCurrency())
                && (quotedExchangeRate == null || quotedExchangeRateDate == null
                || exchangeRate.rate().compareTo(quotedExchangeRate) != 0
                || !exchangeRate.rateDate().equals(quotedExchangeRateDate))) {
            throw new IllegalArgumentException("The exchange-rate quote changed. Review the updated quote and confirm again.");
        }
        BigDecimal amountToCredit = convertAmount(amount, exchangeRate);

        Account source;
        Account destination;
        if (fromId.compareTo(destinationUser.getAccountId()) < 0) {
            source = accountRepository.findByIdForUpdate(fromId)
                    .orElseThrow(() -> new RuntimeException("Sender account not found"));
            destination = accountRepository.findByIdForUpdate(destinationUser.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Recipient account not found"));
        } else {
            destination = accountRepository.findByIdForUpdate(destinationUser.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Recipient account not found"));
            source = accountRepository.findByIdForUpdate(fromId)
                    .orElseThrow(() -> new RuntimeException("Sender account not found"));
        }
        if (!source.isActive() || !destination.isActive()) {
            throw new IllegalStateException("Both customer accounts must be active.");
        }
        if (!source.getCurrency().equals(sourceSnapshot.getCurrency())
                || !destination.getCurrency().equals(destinationSnapshot.getCurrency())) {
            throw new IllegalStateException("Account currencies changed while preparing the transfer. Request a new quote.");
        }

        // Balance check after acquiring row locks prevents concurrent overspending.
        if (source.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds in your account.");
        }

        BigDecimal amountToDebit = amount;
        // 5. Update Descriptions for Mini-Statement
        String userNote = (note == null || note.trim().isEmpty()) ? "No note provided" : note;
        source.setDescription("Last Sent: " + userNote);
        destination.setDescription("Last Received: " + userNote);

        // 6. Execute Money Movement
        source.debit(amountToDebit);
        destination.credit(amountToCredit);

        accountRepository.save(source);
        accountRepository.save(destination);

        // 7. Audit Trail
        String sDesc = "Transfer to " + destination.getAccountId() + " (" + userNote + ")";
        String dDesc = "Transfer from " + source.getAccountId() + " (" + userNote + ")";

        return transactionRepository.save(Transaction.builder()
                .sourceAccountId(fromId)
                .destinationAccountId(toId)
                .sourceAmount(amountToDebit)
                .amount(amountToCredit)
                .sourceCurrency(source.getCurrency())
                .destinationCurrency(destination.getCurrency())
                .exchangeRate(exchangeRate.rate())
                .exchangeRateDate(exchangeRate.rateDate())
                .exchangeRateProvider(exchangeRate.provider())
                .exchangeRateFetchedAt(exchangeRate.fetchedAt())
                .sourceDescription(sDesc)
                .destinationDescription(dDesc)
                .type("TRANSFER")
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build());
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount can have at most two decimal places.");
        }
    }

    private void validateCurrencyAmount(BigDecimal amount, String currency) {
        if (amount.stripTrailingZeros().scale() > CurrencyCode.from(currency).getFractionDigits()) {
            throw new IllegalArgumentException("Amount has too many decimal places for " + currency + ".");
        }
    }

    private BigDecimal convertAmount(BigDecimal amount, ExchangeRateService.ExchangeRate exchangeRate) {
        int fractionDigits = CurrencyCode.from(exchangeRate.quoteCurrency()).getFractionDigits();
        return amount.multiply(exchangeRate.rate()).setScale(fractionDigits, RoundingMode.HALF_UP);
    }

    private User findVerifiedCustomer(String username) {
        User user = userRepository.findByUsername(username)
                .filter(value -> value.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new IllegalArgumentException("Only customers can transfer funds."));
        if (!user.isVerified()) {
            throw new IllegalArgumentException("Transaction denied: sender account is not verified.");
        }
        return user;
    }

    private User findVerifiedRecipient(String accountId) {
        return userRepository.findByAccountId(accountId)
                .filter(user -> user.getRole() == Role.CUSTOMER && user.isVerified())
                .orElseThrow(() -> new IllegalArgumentException("Recipient must be a verified customer account."));
    }

    public List<Transaction> getTransactionHistory(String accountId) {
        return transactionRepository.findByAnyAccountId(accountId);
    }

    public List<Transaction> getAllTransactionsForAdmin() {
        // This pulls every record from the transactions table
        return transactionRepository.findAllTransactionsGlobal();
    }
}