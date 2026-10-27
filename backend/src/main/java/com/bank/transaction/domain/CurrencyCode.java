package com.bank.transaction.domain;

import java.util.Locale;

public enum CurrencyCode {
    INR(2),
    USD(2),
    CNY(2),
    JPY(0);

    private final int fractionDigits;

    CurrencyCode(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }

    public int getFractionDigits() {
        return fractionDigits;
    }

    public static CurrencyCode from(String currency) {
        if (currency != null && !currency.isBlank()) {
            String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
            for (CurrencyCode supportedCurrency : values()) {
                if (supportedCurrency.name().equals(normalizedCurrency)) {
                    return supportedCurrency;
                }
            }
        }
        throw new IllegalArgumentException("Supported currencies are INR, USD, CNY, and JPY.");
    }
}
