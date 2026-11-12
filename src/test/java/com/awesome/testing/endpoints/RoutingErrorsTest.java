package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.dto.user.Role.ROLE_CLIENT;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

/** Unknown paths and unsupported methods use the ErrorDto shape, not Spring's default error body. */
@SuppressWarnings("ConstantConditions")
class RoutingErrorsTest extends DomainHelper {

    private String clientToken;

    @BeforeEach
    void setUp() {
        clientToken = getToken(getRandomUserWithRoles(List.of(ROLE_CLIENT)));
    }

    @Test
    void anUnknownApiPathIsA404ErrorDto() {
        // when
        ResponseEntity<String> response = executeGet("/api/v1/nope", getHeadersWith(clientToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Resource not found\"}");
    }

    @Test
    void anUnsupportedMethodIsA405ErrorDtoWithTheAllowedMethods() {
        // when
        ResponseEntity<String> response = execute(HttpMethod.PATCH, "/api/v1/cart", null,
                getHeadersWith(clientToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"Method PATCH is not supported for this endpoint\"}");
    }
}
