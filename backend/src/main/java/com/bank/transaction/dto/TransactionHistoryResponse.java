package com.bank.transaction.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionHistoryResponse(
        String transactionId,
        String type,
        String status,
        String direction,
        BigDecimal amount,
        String currency,
        LocalDateTime createdAt
) {}