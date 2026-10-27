package com.bank.transaction.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    @Id
    private String accountId;
    private BigDecimal balance;
    private String currency;
    private boolean active;
    private String description;

    // Explicit constructor to match Service calls
    public Account(String accountId, BigDecimal balance, String currency, boolean active) {
        this.accountId = accountId;
        this.balance = balance;
        this.currency = currency;
        this.active = active;
        this.description = "New Account";
    }

    public void debit(BigDecimal amount) {
        if (this.balance == null) this.balance = BigDecimal.ZERO;
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (this.balance == null) this.balance = BigDecimal.ZERO;
        this.balance = this.balance.add(amount);
    }
}