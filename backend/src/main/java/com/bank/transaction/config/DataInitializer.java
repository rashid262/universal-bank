package com.bank.transaction.config;

import com.bank.transaction.domain.User;
import com.bank.transaction.enums.Role;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.UserRepository;
import com.bank.transaction.service.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

@Configuration
public class DataInitializer {

    // The password older versions of this initializer hard-coded; replaced on startup if still in use
    private static final String LEGACY_DEFAULT_PASSWORD = "admin123";
    private static final int MIN_PASSWORD_LENGTH = 12;

    @Bean
    public CommandLineRunner initDatabase(
            UserRepository userRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            PasswordEncoder encoder,
            @Value("${app.admin.initial-password:}") String adminPassword,
            @Value("${app.admin.password-reset:false}") boolean adminPasswordReset) {

        return (args) -> {
            if (adminPassword == null || adminPassword.length() < MIN_PASSWORD_LENGTH
                    || adminPassword.equals(LEGACY_DEFAULT_PASSWORD)) {
                throw new IllegalStateException("ADMIN_INITIAL_PASSWORD must be set to at least "
                        + MIN_PASSWORD_LENGTH + " characters (and not the old default).");
            }

            // ONLY INITIALIZE THE SYSTEM ADMIN
            if (!accountRepository.existsById("UB-0000")) {

                // 1. Create foundation (Added email parameter to match AccountService)
                accountService.createNewUserAccount(
                        "UB-0000",
                        "admin",
                        "admin@universalbank.com",
                        "INR"
                );

                // 2. Fetch and elevate to Admin
                User adminUser = userRepository.findByUsername("admin")
                        .orElseThrow(() -> new RuntimeException("Admin user creation failed"));

                adminUser.setPasswordHash(encoder.encode(adminPassword));
                adminUser.setRole(Role.ADMIN);
                adminUser.setVerified(true); // 👈 Important: Admin must be auto-verified
                adminUser.setVerificationToken(null);

                userRepository.save(adminUser);

                System.out.println("🏛️ Universal Bank Vault Initialized (UB-0000)");
                System.out.println("🔑 Admin user 'admin' created with ADMIN_INITIAL_PASSWORD");
                return;
            }

            // Admin recovery needs server access (an env flag + restart), never just an inbox.
            // Retiring the old hard-coded password uses the same path.
            userRepository.findByUsername("admin")
                    .filter(admin -> !encoder.matches(adminPassword, admin.getPasswordHash()))
                    .filter(admin -> adminPasswordReset
                            || encoder.matches(LEGACY_DEFAULT_PASSWORD, admin.getPasswordHash()))
                    .ifPresent(admin -> {
                        admin.setPasswordHash(encoder.encode(adminPassword));
                        admin.setPasswordChangedAt(Instant.now()); // signs out existing admin sessions
                        userRepository.save(admin);
                        System.out.println("🔑 Admin password set from ADMIN_INITIAL_PASSWORD"
                                + (adminPasswordReset ? " (ADMIN_PASSWORD_RESET). Set ADMIN_PASSWORD_RESET=false now." : "."));
                    });
        };
    }
}
