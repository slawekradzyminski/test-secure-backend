package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Without service tokens the internal API does not exist, whatever token is sent. */
@TestPropertySource(properties = {"warehouse.service-token=", "internal.service-tokens.payment=",
        "internal.service-tokens.notification="})
class InternalApiDisabledTest extends HttpHelper {

    @Test
    void internalApiRespondsNotFoundWhenNoTokenIsConfigured() {
        HttpHeaders withToken = getJsonOnlyHeaders();
        withToken.add("X-Service-Token", "test-warehouse-token");

        ResponseEntity<ErrorDto> withHeader = executeGet("/internal/v1/products", withToken, ErrorDto.class);
        ResponseEntity<ErrorDto> withoutHeader = executeGet("/internal/v1/orders/1", getJsonOnlyHeaders(), ErrorDto.class);
        ResponseEntity<ErrorDto> blankHeader = executePost("/internal/v1/notifications", "{}", blank(), ErrorDto.class);

        assertThat(withHeader.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(withoutHeader.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(blankHeader.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(withHeader.getBody().getMessage()).isEqualTo("Not found");
    }

    @Test
    void eventFeedJwksAndExportRespondNotFoundWhenEveryCallerTokenIsBlank() {
        HttpHeaders paymentToken = getJsonOnlyHeaders();
        paymentToken.add("X-Service-Token", "test-payment-token");
        HttpHeaders notificationToken = getJsonOnlyHeaders();
        notificationToken.add("X-Service-Token", "test-notification-token");

        ResponseEntity<ErrorDto> feed = executeGet("/internal/v1/events?after=0", paymentToken, ErrorDto.class);
        ResponseEntity<ErrorDto> jwks = executeGet("/internal/v1/jwks.json", notificationToken, ErrorDto.class);
        ResponseEntity<ErrorDto> export = executeGet("/internal/v1/webhooks/export", notificationToken, ErrorDto.class);

        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(feed.getBody().getMessage()).isEqualTo("Not found");
        assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void stockEndpointsRespondNotFoundWithoutAWarehouseToken() {
        HttpHeaders withToken = getJsonOnlyHeaders();
        withToken.add("X-Service-Token", "test-warehouse-token");

        ResponseEntity<ErrorDto> levels = executePost("/internal/v1/inventory/levels", "{\"levels\":[]}", withToken,
                ErrorDto.class);
        ResponseEntity<ErrorDto> export = executeGet("/internal/v1/inventory/export", withToken, ErrorDto.class);
        ResponseEntity<ErrorDto> byReservation = executeGet(
                "/internal/v1/orders/by-reservation/00000000-0000-0000-0000-000000000001", withToken, ErrorDto.class);

        assertThat(java.util.List.of(levels, export, byReservation)).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getMessage()).isEqualTo("Not found");
        });
    }

    private HttpHeaders blank() {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", "");
        return headers;
    }
}
