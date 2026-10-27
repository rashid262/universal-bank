package com.bank.transaction.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExchangeRateServiceTest {

    @Test
    void fetchesAndCachesFrankfurterRate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://rates.test/v2/rate/usd/inr"))
                .andRespond(withSuccess(
                        "{\"date\":\"2026-10-16\",\"base\":\"USD\",\"quote\":\"INR\",\"rate\":94.96}",
                        MediaType.APPLICATION_JSON));
        ExchangeRateService service = new ExchangeRateService(builder, "https://rates.test");

        ExchangeRateService.ExchangeRate first = service.getRate("usd", "inr");
        ExchangeRateService.ExchangeRate second = service.getRate("USD", "INR");

        assertEquals(new BigDecimal("94.96"), first.rate());
        assertEquals(LocalDate.of(2026, 10, 16), first.rateDate());
        assertEquals("Frankfurter", first.provider());
        assertEquals(first, second);
        server.verify();
    }

    @Test
    void sameCurrencyRateDoesNotCallProvider() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExchangeRateService service = new ExchangeRateService(builder, "https://rates.test");

        ExchangeRateService.ExchangeRate rate = service.getRate("INR", "INR");

        assertEquals(BigDecimal.ONE, rate.rate());
        assertEquals("Same currency", rate.provider());
        server.verify();
    }

    @Test
    void rejectsUnsupportedCurrencies() {
        ExchangeRateService service = new ExchangeRateService(RestClient.builder(), "https://rates.test");

        assertThrows(IllegalArgumentException.class, () -> service.getRate("INR", "EUR"));
    }

    @Test
    void fetchesChineseYuanToJapaneseYenRate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://rates.test/v2/rate/cny/jpy"))
                .andRespond(withSuccess(
                        "{\"date\":\"2026-10-16\",\"base\":\"CNY\",\"quote\":\"JPY\",\"rate\":21.5}",
                        MediaType.APPLICATION_JSON));
        ExchangeRateService service = new ExchangeRateService(builder, "https://rates.test");

        ExchangeRateService.ExchangeRate rate = service.getRate("CNY", "JPY");

        assertEquals(new BigDecimal("21.5"), rate.rate());
        assertEquals("CNY", rate.baseCurrency());
        assertEquals("JPY", rate.quoteCurrency());
        server.verify();
    }
}
