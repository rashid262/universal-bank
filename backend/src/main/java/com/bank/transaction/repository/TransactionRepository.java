package com.bank.transaction.repository;

import com.bank.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    // We use the exact field names from your Transaction Entity
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccountId = :accId OR t.destinationAccountId = :accId ORDER BY t.createdAt DESC")
    List<Transaction> findByAnyAccountId(@Param("accId") String accId);

    // 2. 🛡️ NEW: Global Monitor - Fetch EVERYTHING for the Admin
    // This allows the Admin to see the "Big Picture" of all money movement.
    @Query("SELECT t FROM Transaction t ORDER BY t.createdAt DESC")
    List<Transaction> findAllTransactionsGlobal();
}