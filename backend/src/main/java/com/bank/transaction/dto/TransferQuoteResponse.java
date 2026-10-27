package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransferQuoteResponse(
        String sourceCurrency,
        String destinationCurrency,
        BigDecimal sourceAmount,
        BigDecimal destinationAmount,
        BigDecimal exchangeRate,
        LocalDate rateDate,
        String rateProvider,
        Instant fetchedAt
) {}
