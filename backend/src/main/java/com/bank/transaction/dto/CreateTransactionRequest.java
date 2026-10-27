package com.bank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateTransactionRequest {




    private String sourceAccountId;

    @NotBlank(message = "Recipient Account Number is required")
    @Pattern(regexp = "^UB-\\d{4}$", message = "Recipient must be in UB-XXXX format")
    private String destinationAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount can have at most two decimal places")
    private BigDecimal amount;

    private String currency;

    private BigDecimal quotedExchangeRate;
    private LocalDate quotedExchangeRateDate;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    // ADD THIS FIELD TO RECEIVE THE NOTE FROM REACT
    private String note;

    // Getters and Setters
    public String getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(String sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public String getDestinationAccountId() { return destinationAccountId; }
    public void setDestinationAccountId(String destinationAccountId) { this.destinationAccountId = destinationAccountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getQuotedExchangeRate() { return quotedExchangeRate; }
    public void setQuotedExchangeRate(BigDecimal quotedExchangeRate) { this.quotedExchangeRate = quotedExchangeRate; }
    public LocalDate getQuotedExchangeRateDate() { return quotedExchangeRateDate; }
    public void setQuotedExchangeRateDate(LocalDate quotedExchangeRateDate) { this.quotedExchangeRateDate = quotedExchangeRateDate; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    // ADD THESE METHODS
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}