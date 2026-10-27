package com.bank.transaction.domain;

import com.bank.transaction.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String accountId;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder.Default
    @Column(nullable = false)
    private boolean isVerified = false;

    // 👈 IMPORTANT: Added for the email verification logic
    private String verificationToken;

    // SHA-256 of the emailed reset token; the raw token is never stored
    private String resetTokenHash;
    private Instant resetTokenExpiresAt;

    // JWTs issued before this instant are rejected (sign out everywhere on password change)
    private Instant passwordChangedAt;

    @Transient
    private String currency;
}