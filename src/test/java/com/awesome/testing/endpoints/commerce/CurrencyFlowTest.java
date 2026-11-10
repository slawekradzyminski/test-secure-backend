package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.currency.CurrencyConversionDto;
import com.awesome.testing.dto.currency.CurrencyDto;
import com.awesome.testing.dto.currency.CurrencyRequestDto;
import com.awesome.testing.entity.currency.CurrencyRateEntity;
import com.awesome.testing.repository.currency.CurrencyRateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyFlowTest extends AbstractCommerceFlowTest {

    private static final String CURRENCIES = "/api/v1/currencies";
    private static final String CONVERT = CURRENCIES + "/convert";
    private static final String ADMIN_CURRENCIES = "/api/v1/admin/currencies";

    @Autowired
    private CurrencyRateRepository currencyRateRepository;

    @BeforeEach
    void seedCurrencies() {
        currencyRateRepository.deleteAll();
        currencyRateRepository.saveAll(List.of(currency("USD", "US Dollar", "$", "1"),
                currency("EUR", "Euro", "€", "1.08"), currency("PLN", "Polish Zloty", "zł", "0.25")));
    }

    @AfterEach
    void removeCurrencies() {
        currencyRateRepository.deleteAll();
    }

    @Test
    void listsCurrencies() {
        ResponseEntity<List<CurrencyDto>> response = executeGet(CURRENCIES, getHeadersWith(clientToken),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(CurrencyDto::getCode).containsExactly("EUR", "PLN", "USD");
        assertThat(response.getBody().get(0).getSymbol()).isEqualTo("€");
        assertThat(response.getBody().get(0).getRateToUsd()).isEqualByComparingTo("1.08");
        assertThat(response.getBody().get(0).getUpdatedAt()).isNotNull();
    }

    @Test
    void currencyEndpointsRequireAuthentication() {
        assertThat(executeGet(CURRENCIES, getJsonOnlyHeaders(), ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(executeGet(CONVERT + "?amount=1&to=EUR", getJsonOnlyHeaders(), ErrorDto.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void convertsBetweenCurrencies() {
        ResponseEntity<CurrencyConversionDto> response = executeGet(CONVERT + "?amount=100&from=PLN&to=eur",
                getHeadersWith(clientToken), CurrencyConversionDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(CurrencyConversionDto.builder().amount(new BigDecimal("100.00"))
                .from("PLN").to("EUR").converted(new BigDecimal("23.15")).rate(new BigDecimal("0.231481")).build());
        assertThat(executeGet(CONVERT + "?amount=10&to=PLN", getHeadersWith(clientToken), CurrencyConversionDto.class)
                .getBody().getConverted()).isEqualByComparingTo("40.00");
    }

    @Test
    void conversionRejectsInvalidInput() {
        ResponseEntity<ErrorDto> missing = executeGet(CONVERT + "?to=EUR", getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> text = executeGet(CONVERT + "?amount=abc&to=EUR", getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> precise = executeGet(CONVERT + "?amount=1.001&to=EUR", getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> noTarget = executeGet(CONVERT + "?amount=1", getHeadersWith(clientToken), ErrorDto.class);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().getMessage()).isEqualTo("amount is required");
        assertThat(text.getBody().getMessage()).isEqualTo("amount must be a number");
        assertThat(precise.getBody().getMessage()).isEqualTo("amount must have at most 2 decimals");
        assertThat(noTarget.getBody().getMessage()).isEqualTo("to is required");
    }

    @Test
    void conversionToAnUnknownCurrencyIsNotFound() {
        ResponseEntity<ErrorDto> response = executeGet(CONVERT + "?amount=1&to=JPY", getHeadersWith(clientToken), ErrorDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Currency not found: JPY");
    }

    @Test
    void adminCreatesAndUpdatesCurrencies() {
        ResponseEntity<CurrencyDto> created = executePut(ADMIN_CURRENCIES + "/gbp", request("British Pound", "£", "1.27"),
                getHeadersWith(adminToken), CurrencyDto.class);
        ResponseEntity<CurrencyDto> updated = executePut(ADMIN_CURRENCIES + "/EUR", request("Euro", "€", "1.10"),
                getHeadersWith(adminToken), CurrencyDto.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getCode()).isEqualTo("GBP");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getRateToUsd()).isEqualByComparingTo("1.10");
        assertThat(executeGet(CONVERT + "?amount=110&from=EUR&to=USD", getHeadersWith(clientToken), CurrencyConversionDto.class)
                .getBody().getConverted()).isEqualByComparingTo("121.00");
    }

    @Test
    void adminCurrencyValidation() {
        ResponseEntity<Map<String, String>> invalid = executePut(ADMIN_CURRENCIES + "/EUR", request("", "", "0"),
                getHeadersWith(adminToken), new ParameterizedTypeReference<>() {});
        ResponseEntity<ErrorDto> base = executePut(ADMIN_CURRENCIES + "/USD", request("US Dollar", "$", "2"),
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> code = executePut(ADMIN_CURRENCIES + "/EURO", request("Euro", "€", "1"),
                getHeadersWith(adminToken), ErrorDto.class);

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).containsEntry("rateToUsd", "Rate to USD must be greater than 0")
                .containsEntry("name", "Currency name is required").containsEntry("symbol", "Currency symbol is required");
        assertThat(base.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(base.getBody().getMessage()).isEqualTo("USD is the base currency; its rate to USD must be 1");
        assertThat(code.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(currencyRateRepository.findById("EUR").orElseThrow().getRateToUsd()).isEqualByComparingTo("1.08");
    }

    @Test
    void onlyAdminsChangeCurrencies() {
        ResponseEntity<ErrorDto> client = executePut(ADMIN_CURRENCIES + "/EUR", request("Euro", "€", "2"),
                getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> anonymous = executePut(ADMIN_CURRENCIES + "/EUR", request("Euro", "€", "2"),
                getJsonOnlyHeaders(), ErrorDto.class);

        assertThat(client.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static CurrencyRequestDto request(String name, String symbol, String rate) {
        return CurrencyRequestDto.builder().name(name).symbol(symbol).rateToUsd(new BigDecimal(rate)).build();
    }

    private static CurrencyRateEntity currency(String code, String name, String symbol, String rate) {
        return CurrencyRateEntity.builder().code(code).name(name).symbol(symbol).rateToUsd(new BigDecimal(rate))
                .updatedAt(LocalDateTime.now()).build();
    }
}
