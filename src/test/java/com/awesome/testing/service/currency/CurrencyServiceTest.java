package com.awesome.testing.service.currency;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.currency.CurrencyConversionDto;
import com.awesome.testing.dto.currency.CurrencyRequestDto;
import com.awesome.testing.entity.currency.CurrencyRateEntity;
import com.awesome.testing.repository.currency.CurrencyRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock private CurrencyRateRepository repository;
    private CurrencyService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyService(repository, CLOCK);
        lenient().when(repository.findById("USD")).thenReturn(Optional.of(currency("USD", "1")));
        lenient().when(repository.findById("EUR")).thenReturn(Optional.of(currency("EUR", "1.08")));
        lenient().when(repository.save(any(CurrencyRateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listsCurrenciesOrderedByCode() {
        when(repository.findAll(Sort.by("code"))).thenReturn(List.of(currency("EUR", "1.08"), currency("USD", "1")));

        assertThat(service.list()).extracting("code").containsExactly("EUR", "USD");
    }

    @Test
    void convertsWithCaseInsensitiveCodes() {
        CurrencyConversionDto result = service.convert(" 100 ", " usd ", "eur");

        assertThat(result.getAmount()).hasToString("100.00");
        assertThat(result.getFrom()).isEqualTo("USD");
        assertThat(result.getTo()).isEqualTo("EUR");
        assertThat(result.getConverted()).hasToString("92.59");
        assertThat(result.getRate()).hasToString("0.925926");
    }

    @Test
    void sourceDefaultsToUsd() {
        assertThat(service.convert("10", null, "EUR").getFrom()).isEqualTo("USD");
    }

    @ParameterizedTest(name = "amount={0} from={1} to={2} -> {3}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "null|USD|EUR|amount is required",
            "'  '|USD|EUR|amount is required",
            "ten|USD|EUR|amount must be a number",
            "NaN|USD|EUR|amount must be a number",
            "1e2|USD|EUR|amount must be a number",
            "1.|USD|EUR|amount must be a number",
            "-abc|USD|EUR|amount must be a number",
            "-0.01|USD|EUR|amount must not be negative",
            "1.001|USD|EUR|amount must have at most 2 decimals",
            "100000000.00|USD|EUR|amount must not exceed 99999999.99",
            "1|''|EUR|from is required",
            "1|US|EUR|from must be a 3-letter ISO 4217 code",
            "1|USD|null|to is required",
            "1|USD|EU1|to must be a 3-letter ISO 4217 code"})
    void rejectsInvalidInput(String amount, String from, String to, String message) {
        assertThatThrownBy(() -> service.convert(amount, from, to))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }

    @Test
    void acceptsBoundaryAmounts() {
        assertThat(service.convert("99999999.99", "USD", "USD").getConverted()).hasToString("99999999.99");
        assertThat(service.convert("0", "USD", "EUR").getConverted()).hasToString("0.00");
        assertThat(service.convert("1.50000", "USD", "USD").getAmount()).hasToString("1.50");
        assertThat(service.convert("+5", "USD", "USD").getAmount()).hasToString("5.00");
    }

    @Test
    void unknownCurrenciesAreNotFound() {
        when(repository.findById("JPY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convert("1", "USD", "jpy"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Currency not found: JPY");
                });
    }

    @Test
    void createsACurrency() {
        when(repository.findById("JPY")).thenReturn(Optional.empty());

        CurrencyUpsert result = service.upsert("jpy", CurrencyRequestDto.builder()
                .name(" Japanese Yen ").symbol(" ¥ ").rateToUsd(new BigDecimal("0.0067")).build());

        assertThat(result.created()).isTrue();
        assertThat(result.currency().getCode()).isEqualTo("JPY");
        assertThat(result.currency().getName()).isEqualTo("Japanese Yen");
        assertThat(result.currency().getSymbol()).isEqualTo("¥");
        assertThat(result.currency().getRateToUsd()).hasToString("0.006700");
        assertThat(result.currency().getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 12, 0));
    }

    @Test
    void updatesAnExistingCurrency() {
        CurrencyUpsert result = service.upsert("EUR", CurrencyRequestDto.builder()
                .name("Euro").symbol("€").rateToUsd(new BigDecimal("1.1")).build());

        assertThat(result.created()).isFalse();
        assertThat(result.currency().getRateToUsd()).hasToString("1.100000");
    }

    @Test
    void theBaseCurrencyKeepsARateOfOne() {
        assertThatThrownBy(() -> service.upsert("usd", CurrencyRequestDto.builder()
                .name("US Dollar").symbol("$").rateToUsd(new BigDecimal("1.01")).build()))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("USD is the base currency; its rate to USD must be 1");
                });
        verify(repository, never()).save(any());

        assertThat(service.upsert("USD", CurrencyRequestDto.builder()
                .name("US Dollar").symbol("US$").rateToUsd(new BigDecimal("1.000")).build()).currency().getSymbol())
                .isEqualTo("US$");
    }

    @Test
    void rejectsMalformedCodesOnUpsert() {
        assertThatThrownBy(() -> service.upsert("EURO", CurrencyRequestDto.builder()
                .name("Euro").symbol("€").rateToUsd(BigDecimal.ONE).build()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Currency code must be a 3-letter ISO 4217 code"));
    }

    private static CurrencyRateEntity currency(String code, String rate) {
        return CurrencyRateEntity.builder().code(code).name(code).symbol(code).rateToUsd(new BigDecimal(rate)).build();
    }
}
