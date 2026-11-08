package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Without WAREHOUSE_SERVICE_TOKEN the internal API does not exist, whatever token is sent. */
@TestPropertySource(properties = "warehouse.service-token=")
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

    private HttpHeaders blank() {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", "");
        return headers;
    }
}
