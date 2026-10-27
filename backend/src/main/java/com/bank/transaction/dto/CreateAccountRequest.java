package com.bank.transaction.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateAccountRequest {
    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Currency is required (e.g., USD, EUR)")
    private String currency;

    // Getters and Setters
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}