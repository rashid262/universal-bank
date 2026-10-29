package com.bank.transaction.service;

import com.bank.transaction.domain.Account;
import com.bank.transaction.domain.User;
import com.bank.transaction.dto.AuthRequest;
import com.bank.transaction.dto.RegisterRequest;
import com.bank.transaction.enums.Role;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.UserRepository;
import com.bank.transaction.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, accountRepository, passwordEncoder, mailSender, jwtUtil);
    }

    @Test
    void registrationCannotOverwriteTheVault() {
        when(accountRepository.existsById("UB-0000")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request("UB-0000")));

        verify(accountRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registrationCannotOverwriteAnExistingCustomerAccount() {
        when(accountRepository.existsById("UB-4721")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request("UB-4721")));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void registrationRejectsTakenUsername() {
        when(userRepository.existsByUsername("new_customer")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> authService.register(request("UB-5001")));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void registrationCreatesNewUnverifiedCustomerWithZeroBalance() {
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("hashed");

        authService.register(request("UB-5001"));

        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(account.capture());
        assertEquals("UB-5001", account.getValue().getAccountId());
        assertEquals(0, BigDecimal.ZERO.compareTo(account.getValue().getBalance()));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertNull(user.getValue().getId());
        assertEquals(Role.CUSTOMER, user.getValue().getRole());
        assertFalse(user.getValue().isVerified());
        assertEquals("hashed", user.getValue().getPasswordHash());
        assertNotNull(user.getValue().getVerificationToken());
    }

    @Test
    void loginGivesSameMessageForUnknownUserAndWrongPassword() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(User.builder().username("admin").passwordHash("hashed").build()));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        RuntimeException unknown = assertThrows(RuntimeException.class,
                () -> authService.login(new AuthRequest("ghost", "wrong")));
        RuntimeException wrongPassword = assertThrows(RuntimeException.class,
                () -> authService.login(new AuthRequest("admin", "wrong")));

        assertEquals(unknown.getMessage(), wrongPassword.getMessage());
    }

    @Test
    void unverifiedStatusIsOnlyRevealedAfterCorrectPassword() {
        when(userRepository.findByUsername("new_customer"))
                .thenReturn(Optional.of(User.builder().username("new_customer").passwordHash("hashed").build()));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> authService.login(new AuthRequest("new_customer", "wrong")));

        assertFalse(error.getMessage().contains("verify"));
    }

    @Test
    void changePasswordRejectsReusingCurrentPassword() {
        when(userRepository.findByUsername("new_customer"))
                .thenReturn(Optional.of(User.builder().username("new_customer").passwordHash("hashed").build()));
        when(passwordEncoder.matches("same-pass-123", "hashed")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword("new_customer", "same-pass-123", "same-pass-123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRevokesOldSessionsAndReturnsFreshToken() {
        User user = User.builder().username("new_customer").passwordHash("hashed").role(Role.CUSTOMER).build();
        when(userRepository.findByUsername("new_customer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass-123", "hashed")).thenReturn(true);
        when(passwordEncoder.matches("new-pass-123", "hashed")).thenReturn(false);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("new-hash");
        when(jwtUtil.generateToken("new_customer", "CUSTOMER")).thenReturn("fresh-jwt");

        String token = authService.changePassword("new_customer", "old-pass-123", "new-pass-123");

        assertEquals("fresh-jwt", token);
        assertEquals("new-hash", user.getPasswordHash());
        assertNotNull(user.getPasswordChangedAt());
    }

    @Test
    void forgotPasswordIgnoresAdminAccounts() {
        User admin = User.builder().username("admin").email("admin@universalbank.com")
                .role(Role.ADMIN).isVerified(true).build();
        when(userRepository.findByEmailIgnoreCase("admin@universalbank.com")).thenReturn(Optional.of(admin));

        authService.requestPasswordReset("admin@universalbank.com");

        assertNull(admin.getResetTokenHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void forgotPasswordIsSilentForUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.requestPasswordReset("nobody@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void forgotPasswordStoresOnlyHashedTokenWithExpiry() {
        User customer = User.builder().username("new_customer").email("new.customer@example.com")
                .role(Role.CUSTOMER).isVerified(true).build();
        when(userRepository.findByEmailIgnoreCase("new.customer@example.com")).thenReturn(Optional.of(customer));

        authService.requestPasswordReset("new.customer@example.com");

        assertNotNull(customer.getResetTokenHash());
        assertEquals(64, customer.getResetTokenHash().length()); // hex SHA-256, not the raw token
        assertTrue(customer.getResetTokenExpiresAt().isAfter(Instant.now()));
        verify(userRepository).save(customer);
    }

    @Test
    void resetPasswordWithValidTokenSetsPasswordAndConsumesToken() {
        User customer = User.builder().username("new_customer").role(Role.CUSTOMER)
                .resetTokenHash(AuthService.sha256("raw-token"))
                .resetTokenExpiresAt(Instant.now().plusSeconds(600)).build();
        when(userRepository.findByResetTokenHash(AuthService.sha256("raw-token"))).thenReturn(Optional.of(customer));
        when(passwordEncoder.encode("brand-new-pass")).thenReturn("new-hash");

        authService.resetPassword("raw-token", "brand-new-pass");

        assertEquals("new-hash", customer.getPasswordHash());
        assertNull(customer.getResetTokenHash());
        assertNull(customer.getResetTokenExpiresAt());
        assertNotNull(customer.getPasswordChangedAt());
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        User customer = User.builder().username("new_customer").role(Role.CUSTOMER).passwordHash("hashed")
                .resetTokenHash(AuthService.sha256("raw-token"))
                .resetTokenExpiresAt(Instant.now().minusSeconds(1)).build();
        when(userRepository.findByResetTokenHash(AuthService.sha256("raw-token"))).thenReturn(Optional.of(customer));

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword("raw-token", "brand-new-pass"));
        assertEquals("hashed", customer.getPasswordHash());
    }

    @Test
    void registrationReportsWhenVerificationEmailFails() {
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("hashed");
        doThrow(new MailAuthenticationException("525 Unauthorized IP address"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertFalse(authService.register(request("UB-5001")));
        verify(userRepository).save(any(User.class)); // account is kept so a new link can be requested
    }

    @Test
    void resendVerificationIssuesFreshTokenForUnverifiedCustomer() {
        User customer = User.builder().username("new_customer").email("new.customer@example.com")
                .role(Role.CUSTOMER).isVerified(false).verificationToken("old-token").build();
        when(userRepository.findByEmailIgnoreCase("new.customer@example.com")).thenReturn(Optional.of(customer));

        authService.resendVerification("new.customer@example.com");

        assertNotNull(customer.getVerificationToken());
        assertNotEquals("old-token", customer.getVerificationToken());
        verify(userRepository).save(customer);
    }

    @Test
    void resendVerificationIgnoresVerifiedAccounts() {
        User customer = User.builder().username("new_customer").email("new.customer@example.com")
                .role(Role.CUSTOMER).isVerified(true).build();
        when(userRepository.findByEmailIgnoreCase("new.customer@example.com")).thenReturn(Optional.of(customer));

        authService.resendVerification("new.customer@example.com");

        assertNull(customer.getVerificationToken());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendVerificationIsRateLimitedPerEmail() {
        User customer = User.builder().username("new_customer").email("new.customer@example.com")
                .role(Role.CUSTOMER).isVerified(false).build();
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(customer));

        authService.resendVerification("new.customer@example.com");
        String firstToken = customer.getVerificationToken();
        authService.resendVerification("NEW.customer@example.com");

        assertEquals(firstToken, customer.getVerificationToken());
        verify(userRepository, times(1)).save(customer);
    }

    private RegisterRequest request(String accountId) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new_customer");
        request.setEmail("new.customer@example.com");
        request.setPassword("s3cret-pass");
        request.setAccountId(accountId);
        request.setCurrency("INR");
        return request;
    }
}
