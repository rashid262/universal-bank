package com.bank.transaction.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor // Added for JSON compatibility
@AllArgsConstructor
public class AccountBalanceResponse {
    private String accountId;
    private BigDecimal balance;
    private String currency;
}