package com.awesome.testing.security;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Catalog X-23: a client sending an invalid body to an administrator endpoint received a 400 with the
 * administrator validation rules, because {@code @PreAuthorize} runs after body validation.
 */
class AuthorizationBeforeValidationTest extends DomainHelper {

    private static final Map<String, String> INVALID_BODY = Map.of("name", "QA");

    @ParameterizedTest
    @CsvSource({
        "POST, /api/v1/products",
        "PUT, /api/v1/products/1",
        "PUT, /api/v1/orders/1/status",
        "POST, /api/v1/admin/coupons",
        "POST, /api/v1/admin/gift-cards",
        "PUT, /api/v1/admin/vat-rates/PL",
        "POST, /api/v1/admin/inventory/1/adjustments"
    })
    void clientsGet403WithoutTheAdministratorValidationRules(String method, String path) {
        // given
        String clientToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        var response = execute(HttpMethod.valueOf(method), path, INVALID_BODY, getHeadersWith(clientToken),
                String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().replaceAll("\\s", "")).isEqualTo("{\"message\":\"Accessdenied\"}");
    }

    @Test
    void administratorsStillGetTheValidationErrors() {
        String adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));

        var response = executePost("/api/v1/products", INVALID_BODY, getHeadersWith(adminToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void profileEditsOfAnotherAccountAreForbiddenBeforeValidationAndOwnEditsAreValidated() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        Map<String, String> invalidEmail = Map.of("email", "not-an-email");

        // when
        var foreign = executePut(getUserEndpoint("someone-else"), invalidEmail, getHeadersWith(clientToken),
                String.class);
        var own = executePut(getUserEndpoint(client.getUsername()), invalidEmail, getHeadersWith(clientToken),
                String.class);
        var prompt = executePut("/api/v1/users/chat-system-prompt", Map.of("chatSystemPrompt", "Be brief"),
                getHeadersWith(clientToken), String.class);

        // then
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(prompt.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
