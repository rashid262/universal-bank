package com.bank.transaction.service;

import com.bank.transaction.domain.Account;
import com.bank.transaction.domain.CurrencyCode;
import com.bank.transaction.domain.Transaction;
import com.bank.transaction.domain.User;
import com.bank.transaction.enums.Role;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.repository.UserRepository;
import com.bank.transaction.dto.TransferQuoteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AccountService {

    private static final String VAULT_ACCOUNT_ID = "UB-0000";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final ExchangeRateService exchangeRateService;

    @Value("${app.verification.url}")
    private String verificationBaseUrl;

    private static final String ACCOUNT_FORMAT = "^UB-\\d{4}$";

    // 🛡️ Password Generation Constants
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          PasswordEncoder passwordEncoder,
                          UserRepository userRepository,
                          JavaMailSender mailSender,
                          ExchangeRateService exchangeRateService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.exchangeRateService = exchangeRateService;
    }

    public Account getAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
    }

    @Transactional
    public Transaction creditAccount(String accountId, BigDecimal amount, String currency, String idempotencyKey) {
        return creditAccount(accountId, amount, currency, idempotencyKey, null, null);
    }

    @Transactional(readOnly = true)
    public TransferQuoteResponse quoteCredit(String accountId, BigDecimal amount) {
        validateCreditAmount(amount);
        Account vault = accountRepository.findById(VAULT_ACCOUNT_ID)
                .orElseThrow(() -> new IllegalStateException("System vault account is not initialized."));
        User recipient = userRepository.findByAccountId(accountId)
                .filter(user -> user.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new IllegalArgumentException("Credits can only be sent to customer accounts."));
        Account account = accountRepository.findById(recipient.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Customer account not found."));
        validateCreditAccounts(vault, account);
        if (vault.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in the system vault.");
        }
        return createCreditQuote(amount, vault.getCurrency(), account.getCurrency());
    }

    @Transactional
    public Transaction creditAccount(
            String accountId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            BigDecimal quotedExchangeRate,
            java.time.LocalDate quotedExchangeRateDate
    ) {
        validateMoneyRequest(amount, idempotencyKey);
        rejectDuplicateIdempotencyKey(idempotencyKey);
        validateCreditAmount(amount);
        if (VAULT_ACCOUNT_ID.equals(accountId)) {
            throw new IllegalArgumentException("The vault cannot be credited through the customer disbursement endpoint.");
        }

        User recipient = userRepository.findByAccountId(accountId)
                .filter(user -> user.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new IllegalArgumentException("Credits can only be sent to customer accounts."));
        Account vault = accountRepository.findByIdForUpdate(VAULT_ACCOUNT_ID)
                .orElseThrow(() -> new IllegalStateException("System vault account is not initialized."));
        Account account = accountRepository.findByIdForUpdate(recipient.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Customer account not found."));
        validateCreditAccounts(vault, account);
        if (currency != null && !currency.isBlank() && !vault.getCurrency().equalsIgnoreCase(currency.trim())) {
            throw new IllegalArgumentException("Credit source currency must match the system vault currency.");
        }
        if (vault.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in the system vault.");
        }

        ExchangeRateService.ExchangeRate rate = exchangeRateService.getRate(vault.getCurrency(), account.getCurrency());
        if (!vault.getCurrency().equals(account.getCurrency())
                && (quotedExchangeRate == null || quotedExchangeRateDate == null
                || rate.rate().compareTo(quotedExchangeRate) != 0
                || !rate.rateDate().equals(quotedExchangeRateDate))) {
            throw new IllegalArgumentException("The exchange-rate quote changed. Review the updated quote and confirm again.");
        }
        BigDecimal creditedAmount = amount.multiply(rate.rate())
                .setScale(CurrencyCode.from(account.getCurrency()).getFractionDigits(), java.math.RoundingMode.HALF_UP);

        vault.debit(amount);
        account.credit(creditedAmount);
        accountRepository.save(vault);
        accountRepository.save(account);

        return transactionRepository.save(Transaction.builder()
                .sourceAccountId(VAULT_ACCOUNT_ID)
                .destinationAccountId(accountId)
                .sourceAmount(amount)
                .amount(creditedAmount)
                .sourceCurrency(vault.getCurrency())
                .destinationCurrency(account.getCurrency())
                .exchangeRate(rate.rate())
                .exchangeRateDate(rate.rateDate())
                .exchangeRateProvider(rate.provider())
                .exchangeRateFetchedAt(rate.fetchedAt())
                .type("CREDIT")
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .sourceDescription("System vault disbursement")
                .destinationDescription(vault.getCurrency().equals(account.getCurrency())
                        ? "Admin-authorized vault credit"
                        : "Admin-authorized vault credit converted at " + rate.provider() + " reference rate")
                .build());
    }

    private TransferQuoteResponse createCreditQuote(BigDecimal amount, String sourceCurrency, String destinationCurrency) {
        ExchangeRateService.ExchangeRate rate = exchangeRateService.getRate(sourceCurrency, destinationCurrency);
        BigDecimal convertedAmount = amount.multiply(rate.rate())
                .setScale(CurrencyCode.from(destinationCurrency).getFractionDigits(), java.math.RoundingMode.HALF_UP);
        return new TransferQuoteResponse(
                sourceCurrency, destinationCurrency, amount, convertedAmount,
                rate.rate(), rate.rateDate(), rate.provider(), rate.fetchedAt());
    }

    private void validateCreditAccounts(Account vault, Account customer) {
        if (!vault.isActive() || !customer.isActive()) {
            throw new IllegalStateException("Both the vault and customer account must be active.");
        }
        CurrencyCode.from(vault.getCurrency());
        CurrencyCode.from(customer.getCurrency());
    }

    private void validateCreditAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > CurrencyCode.from("INR").getFractionDigits()) {
            throw new IllegalArgumentException("Vault credit amount can have at most two decimal places.");
        }
    }

    @Transactional
    public Transaction fundVault(BigDecimal amount, String idempotencyKey) {
        validateMoneyRequest(amount, idempotencyKey);
        rejectDuplicateIdempotencyKey(idempotencyKey);
        Account vault = accountRepository.findByIdForUpdate(VAULT_ACCOUNT_ID)
                .orElseThrow(() -> new IllegalStateException("System vault account is not initialized."));
        if (!vault.isActive()) {
            throw new IllegalStateException("System vault account is inactive.");
        }

        vault.credit(amount);
        accountRepository.save(vault);
        return transactionRepository.save(Transaction.builder()
                .sourceAccountId("OPENING_BALANCE")
                .destinationAccountId(VAULT_ACCOUNT_ID)
                .sourceAmount(amount)
                .amount(amount)
                .type("VAULT_FUNDING")
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .sourceDescription("Authorized local demo opening balance")
                .destinationDescription("System vault opening balance")
                .build());
    }

    private void validateMoneyRequest(BigDecimal amount, String idempotencyKey) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required.");
        }
    }

    private void rejectDuplicateIdempotencyKey(String idempotencyKey) {
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new IllegalArgumentException("This idempotency key has already been used.");
        }
    }

    @Transactional
    public Account createNewUserAccount(String accountId, String username, String email, String currency) {
        String supportedCurrency = CurrencyCode.from(currency == null || currency.isBlank() ? "INR" : currency).name();
        // Validation Checks
        if (!accountId.matches(ACCOUNT_FORMAT)) {
            throw new RuntimeException("Format Error: Use UB-XXXX");
        }
        if (accountRepository.existsById(accountId)) {
            throw new RuntimeException("Account ID " + accountId + " already exists.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email " + email + " is already registered.");
        }

        // 1. Generate Secure Random Password for Admin-created user
        String tempPassword = generateRandomPassword();

        // 2. Create Bank Account
        Account account = new Account(accountId, BigDecimal.ZERO, supportedCurrency, true);
        account.setDescription("Account provisioned by Administrator");
        accountRepository.save(account);

        // 3. Create User with verification token and encoded temp password
        String vToken = UUID.randomUUID().toString();
        User newUser = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .accountId(accountId)
                .role(Role.CUSTOMER)
                .isVerified(false)
                .verificationToken(vToken)
                .build();
        userRepository.save(newUser);

        // 4. Send email containing BOTH the link and the password
        sendProvisioningEmail(email, vToken, tempPassword);

        return account;
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    private void sendProvisioningEmail(String email, String token, String tempPass) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("rashidansari.an697@gmail.com");
            message.setTo(email);
            message.setSubject("Universal Bank - New Account Created");

            String content = "Welcome to Universal Bank!\n\n" +
                    "An administrator has created an account for you.\n" +
                    "Your Temporary Password: " + tempPass + "\n\n" +
                    "Please click the link below to verify your account and enable login:\n" +
                    verificationBaseUrl + token + "\n\n" +
                    "Note: For security, change your password immediately after your first login.";

            message.setText(content);
            mailSender.send(message);
            System.out.println("✅ Provisioning email successfully sent to: " + email);
        } catch (Exception e) {
            System.err.println("❌ Mail Error: " + e.getClass().getName());
            System.err.println("❌ Error Message: " + e.getMessage());
            e.printStackTrace();
            System.out.println("⚠️ EMAIL FAILED for " + email + "; the account stays unverified until a link is delivered.");
        }
    }
}