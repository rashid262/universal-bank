package com.bank.transaction.repository;

import com.bank.transaction.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByVerificationToken(String token);

    // 👈 ADD THIS: To find the owner by their Bank ID
    Optional<User> findByAccountId(String accountId);

    // 👈 ADD THIS: To check if an email is already in use
    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByResetTokenHash(String resetTokenHash);
}