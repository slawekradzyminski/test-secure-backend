package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** internal.service-tokens.warehouse takes precedence over its warehouse.service-token alias. */
@TestPropertySource(properties = {
        "warehouse.service-token=alias-token",
        "internal.service-tokens.warehouse=named-token"})
class InternalServiceTokenPropertiesTest extends HttpHelper {

    @Test
    void theNamedWarehouseTokenWinsOverTheAlias() {
        ResponseEntity<String> named = executeGet("/internal/v1/products", serviceHeaders("named-token"), String.class);
        ResponseEntity<ErrorDto> alias = executeGet("/internal/v1/products", serviceHeaders("alias-token"), ErrorDto.class);

        assertThat(named.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(alias.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders serviceHeaders(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", token);
        return headers;
    }
}
