package com.bank.transaction.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bank.transaction.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ExchangeRateService {

    private static final String PROVIDER = "Frankfurter";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private final RestClient restClient;
    private final ConcurrentMap<String, CachedRate> rates = new ConcurrentHashMap<>();

    public ExchangeRateService(
            RestClient.Builder restClientBuilder,
            @Value("${app.fx.base-url:https://api.frankfurter.dev}") String baseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public ExchangeRate getRate(String baseCurrency, String quoteCurrency) {
        String base = normalizeCurrency(baseCurrency);
        String quote = normalizeCurrency(quoteCurrency);
        CurrencyCode.from(base);
        CurrencyCode.from(quote);
        if (base.equals(quote)) {
            return new ExchangeRate(base, quote, BigDecimal.ONE, LocalDate.now(), "Same currency", Instant.now());
        }

        String key = base + "/" + quote;
        Instant now = Instant.now();
        CachedRate cached = rates.get(key);
        if (cached != null && now.isBefore(cached.fetchedAt().plus(CACHE_TTL))) {
            return cached.rate();
        }

        FrankfurterResponse response;
        try {
            response = restClient.get()
                    .uri("/v2/rate/{base}/{quote}",
                            base.toLowerCase(Locale.ROOT), quote.toLowerCase(Locale.ROOT))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw new IllegalStateException("The exchange-rate provider returned HTTP "
                                + result.getStatusCode().value() + ".");
                    })
                    .body(FrankfurterResponse.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Exchange rates are temporarily unavailable; no conversion was applied.",
                    exception);
        }

        if (response == null || response.rate() == null || response.rate().signum() <= 0
                || response.date() == null
                || !base.equalsIgnoreCase(response.base())
                || !quote.equalsIgnoreCase(response.quote())) {
            throw new IllegalStateException("The exchange-rate provider returned an invalid rate response.");
        }

        ExchangeRate rate = new ExchangeRate(base, quote, response.rate(), response.date(), PROVIDER, now);
        rates.put(key, new CachedRate(rate, now));
        return rate;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required.");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    public record ExchangeRate(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            LocalDate rateDate,
            String provider,
            Instant fetchedAt
    ) {}

    private record FrankfurterResponse(LocalDate date, String base, String quote, BigDecimal rate) {}

    private record CachedRate(ExchangeRate rate, Instant fetchedAt) {}
}
