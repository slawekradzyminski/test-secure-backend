package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.dto.user.Role.ROLE_ADMIN;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

/** Unreadable bodies, unconvertible parameters and unsupported media types (catalog T-006). */
@SuppressWarnings("ConstantConditions")
class RequestFormatErrorsTest extends DomainHelper {

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = getToken(getRandomUserWithRoles(List.of(ROLE_ADMIN)));
    }

    @Test
    void malformedJsonIsAValidationErrorWithoutParserText() {
        // when
        ResponseEntity<Map<String, String>> response = executePost(LOGIN_ENDPOINT, "{\"username\":",
                getJsonOnlyHeaders(), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Malformed JSON request body"));
    }

    @Test
    void aJsonArrayInsteadOfAnObjectIsMalformed() {
        // when
        ResponseEntity<Map<String, String>> response = executePost(REGISTER_ENDPOINT, "[]",
                getJsonOnlyHeaders(), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Malformed JSON request body"));
    }

    @Test
    void aMissingBodyIsReported() {
        // when
        ResponseEntity<Map<String, String>> response = execute(HttpMethod.POST, LOGIN_ENDPOINT, null,
                getJsonOnlyHeaders(), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Request body is required"));
    }

    @Test
    void anUnknownEnumBodyListsTheAllowedValues() {
        // when
        ResponseEntity<Map<String, String>> response = executePut("/api/v1/orders/1/status", "\"NOT_A_STATUS\"",
                getHeadersWith(adminToken), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).startsWith("Request body must be one of: PENDING, ")
                .doesNotContain("com.awesome");
    }

    @Test
    void aMalformedUuidFieldIsAFieldError() {
        // when
        ResponseEntity<Map<String, String>> response = executePost("/api/v1/admin/inventory/1/adjustments",
                "{\"delta\":1,\"reason\":\"Recount\",\"requestId\":\"not-a-uuid\"}",
                getHeadersWith(adminToken), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("requestId", "Must be a valid UUID"));
    }

    @Test
    void aNonNumericPathIdNamesTheParameter() {
        // when
        ResponseEntity<Map<String, String>> response = executeGet("/api/v1/products/abc",
                getHeadersWith(adminToken), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Invalid id 'abc': must be a whole number"));
    }

    @Test
    void anUnknownEnumQueryParameterKeepsTheErrorKeyAndTheRejectedValue() {
        // when
        ResponseEntity<Map<String, String>> response = executeGet("/api/v1/orders?status=NOT_A_STATUS",
                getHeadersWith(adminToken), mapTypeReference());

        // then: the course suite asserts body.error contains the rejected value
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).contains("NOT_A_STATUS").startsWith("Unknown status")
                .doesNotContain("com.awesome");
    }

    @Test
    void anUnsupportedContentTypeIsAnErrorDto() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "text/plain");
        headers.add(HttpHeaders.ACCEPT, "application/json");

        // when
        ResponseEntity<ErrorDto> response = executePost(LOGIN_ENDPOINT, "username=admin", headers, ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().getMessage()).isEqualTo("Content type text/plain is not supported");
    }

    @Test
    void anUnacceptableResponseTypeIsAnErrorDto() {
        // given
        HttpHeaders headers = getHeadersWith(adminToken);
        headers.set(HttpHeaders.ACCEPT, "image/png");

        // when
        ResponseEntity<String> response = executeGet("/api/v1/products", headers, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"The requested response media type is not available\"}");
    }

    @Test
    void theOllamaProxyKeepsItsOwnUnreadableBodyFormat() {
        // given
        HttpHeaders headers = getHeadersWith(adminToken);
        headers.set(HttpHeaders.ACCEPT, "text/event-stream");

        // when
        ResponseEntity<Map<String, Object>> response = executePost("/api/v1/ollama/chat", "{\"model\":",
                headers, new org.springframework.core.ParameterizedTypeReference<>() {});

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKeys("status", "message", "timestamp");
    }
}
