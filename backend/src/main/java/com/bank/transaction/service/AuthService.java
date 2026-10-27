package com.bank.transaction.service;

import com.bank.transaction.domain.Account;
import com.bank.transaction.domain.CurrencyCode;
import com.bank.transaction.domain.User;
import com.bank.transaction.dto.AuthRequest;
import com.bank.transaction.dto.AuthResponse;
import com.bank.transaction.dto.RegisterRequest;
import com.bank.transaction.enums.Role;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.UserRepository;
import com.bank.transaction.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender; // Changed from RestTemplate
    private final JwtUtil jwtUtil;

    @Value("${app.verification.url}")
    private String verificationBaseUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String mailFrom;

    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository, AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder, JavaMailSender mailSender, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse login(AuthRequest request) {
        // Same message for unknown user and wrong password so usernames cannot be enumerated
        User user = userRepository.findByUsername(request.getUsername())
                .filter(found -> request.getPassword() != null
                        && passwordEncoder.matches(request.getPassword(), found.getPasswordHash()))
                .orElseThrow(() -> new RuntimeException("Invalid username or password."));

        // Only revealed after the password is proven, so it leaks nothing to a guesser
        if (!user.isVerified()) {
            throw new RuntimeException("Please verify your email address before logging in.");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, user.getUsername(), user.getAccountId(), user.getRole().name());
    }

    @Transactional
    public String register(RegisterRequest request) {
        String email = request.getEmail().trim();
        String username = request.getUsername().trim();
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already in use!");
        }
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already in use!");
        }

        // Build the entity from allow-listed fields only; never bind client JSON to the User entity.
        User user = User.builder()
                .username(username)
                .email(email)
                .accountId(request.getAccountId())
                .currency(request.getCurrency())
                .build();
        prepareAccountAndUser(user, request.getPassword(), false);

        String vToken = UUID.randomUUID().toString();
        user.setVerificationToken(vToken);
        userRepository.save(user);

        sendEmail(user.getEmail(), vToken, null);
        return "Registration successful! Check your email to verify.";
    }

    @Transactional
    public String provisionByAdmin(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Account already exists with this email.");
        }

        String randomPass = generateRandomPassword();
        prepareAccountAndUser(user, randomPass, true);

        String vToken = UUID.randomUUID().toString();
        user.setVerificationToken(vToken);
        userRepository.save(user);

        sendEmail(user.getEmail(), vToken, randomPass);
        return "Account provisioned. Secure password sent to " + user.getEmail();
    }

    private void prepareAccountAndUser(User user, String rawPassword, boolean isAdminCreated) {
        // save() on an assigned ID merges, so an existing account (including the vault) would be overwritten
        if (user.getAccountId() == null || !user.getAccountId().matches("^UB-\\d{4}$")) {
            throw new IllegalArgumentException("Account numbers use the format UB-0000.");
        }
        if (accountRepository.existsById(user.getAccountId())) {
            throw new IllegalArgumentException("Account number " + user.getAccountId() + " is already taken.");
        }
        String currency = CurrencyCode.from(user.getCurrency() != null ? user.getCurrency() : "INR").name();
        Account newAccount = new Account(user.getAccountId(), BigDecimal.ZERO,
                currency, true);
        newAccount.setDescription(isAdminCreated ? "Created by System Admin" : "Self-registered account");
        accountRepository.save(newAccount);

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.CUSTOMER);
        user.setVerified(false);
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    public void sendEmail(String email, String token, String tempPass) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            // MAIL_FROM must be a sender verified in Brevo
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("Universal Bank - Verify Your Account");

            String body = "Welcome to Universal Bank!\n\n" +
                    (tempPass != null ? "Temp Password: " + tempPass + "\n" : "") +
                    "Verify here: " + verificationBaseUrl + token;

            message.setText(body);

            mailSender.send(message);
            System.out.println("✅ Email successfully sent to: " + email);
        } catch (Exception e) {
            System.err.println("❌ Email Error Type: " + e.getClass().getName());
            System.err.println("❌ Error Message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Changes the signed-in user's password, signs out every other session,
     * and returns a fresh JWT so the current browser stays signed in.
     */
    @Transactional
    public String changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new RuntimeException("The current password you entered is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Choose a new password that differs from the current one.");
        }

        applyNewPassword(user, newPassword);
        userRepository.save(user);
        return jwtUtil.generateToken(user.getUsername(), user.getRole().name());
    }

    /**
     * Emails a single-use reset link to a verified customer. Always completes silently so the
     * response never reveals whether an email is registered. Admin accounts are never reset by
     * email; they recover through ADMIN_PASSWORD_RESET on the server.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(user -> user.getRole() == Role.CUSTOMER && user.isVerified())
                .ifPresent(user -> {
                    String rawToken = generateResetToken();
                    user.setResetTokenHash(sha256(rawToken));
                    user.setResetTokenExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
                    userRepository.save(user);
                    // Send off the request thread so response time doesn't reveal which emails exist
                    String to = user.getEmail();
                    CompletableFuture.runAsync(() -> sendPasswordResetEmail(to, rawToken));
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = userRepository.findByResetTokenHash(sha256(rawToken))
                .filter(found -> found.getRole() == Role.CUSTOMER)
                .filter(found -> found.getResetTokenExpiresAt() != null
                        && Instant.now().isBefore(found.getResetTokenExpiresAt()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "This reset link is invalid or has expired. Request a new one."));

        applyNewPassword(user, newPassword);
        userRepository.save(user);
    }

    // Every password change also clears any pending reset link and invalidates existing JWTs
    private void applyNewPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetTokenHash(null);
        user.setResetTokenExpiresAt(null);
        user.setPasswordChangedAt(Instant.now());
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void sendPasswordResetEmail(String email, String rawToken) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("Universal Bank - Reset Your Password");
            message.setText("We received a request to reset your Universal Bank password.\n\n" +
                    "Choose a new password here (the link works once and expires in "
                    + RESET_TOKEN_TTL.toMinutes() + " minutes):\n" +
                    frontendUrl + "/reset-password?token=" + rawToken + "\n\n" +
                    "If you did not ask for this, ignore this email; your password stays the same.");
            mailSender.send(message);
            System.out.println("✅ Password reset email sent to: " + email);
        } catch (Exception e) {
            System.err.println("❌ Password reset email failed for " + email + ": " + e.getMessage());
        }
    }
}