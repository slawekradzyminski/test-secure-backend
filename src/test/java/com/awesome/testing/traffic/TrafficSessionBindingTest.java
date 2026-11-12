package com.awesome.testing.traffic;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Catalog X-03: a traffic session id was the only key to its records, so anyone who knew it read the
 * owner's sign-in bodies, bearer header and tokens; and secrets were stored in clear unless a profile
 * switched masking on. Runs with the test defaults: no masking configured, no legacy public access.
 */
class TrafficSessionBindingTest extends DomainHelper {

    private static final String LOGS = "/api/v1/traffic/logs";

    @Autowired private ObjectMapper mapper;

    @Test
    void anotherUserWithTheSameSessionIdSeesNoneOfTheOwnersTraffic() {
        // given: alice mistypes her password, signs in and loads her profile in one browser session
        String session = "victim-" + UUID.randomUUID();
        UserRegisterDto alice = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(alice);
        signIn(alice.getUsername(), "wrong-password", session);
        String aliceToken = mapper.readTree(signIn(alice.getUsername(), alice.getPassword(), session))
                .path("token").asString();
        executeGet("/api/v1/users/me", withSession(getHeadersWith(aliceToken), session), String.class);
        // and mallory, signed in as herself, knows the session id
        String malloryToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        HttpHeaders mallory = withSession(getHeadersWith(malloryToken), session);

        // when
        JsonNode malloryPage = page(mallory);
        JsonNode alicePage = page(withSession(getHeadersWith(aliceToken), session));
        String aliceRecord = alicePage.at("/content/0/correlationId").asString();
        var malloryDetail = executeGet(LOGS + "/" + aliceRecord, mallory, String.class);

        // then
        assertThat(malloryPage.path("totalElements").asLong()).isZero();
        assertThat(malloryDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(paths(alicePage)).containsExactlyInAnyOrder(
                "POST /api/v1/users/signin 422", "POST /api/v1/users/signin 200", "GET /api/v1/users/me 200");
    }

    @Test
    void storesSignInAndPasswordResetSecretsMaskedWithoutAnyMaskingConfigured() {
        // given
        String session = "masked-" + UUID.randomUUID();
        UserRegisterDto alice = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(alice);
        String aliceToken = mapper.readTree(signIn(alice.getUsername(), alice.getPassword(), session))
                .path("token").asString();
        executePost("/api/v1/users/password/reset",
                Map.of("token", "reset-token-value", "newPassword", "BrandNew123!", "confirmPassword", "BrandNew123!"),
                withSession(getJsonOnlyHeaders(), session), String.class);
        HttpHeaders aliceHeaders = withSession(getHeadersWith(aliceToken), session);
        executeGet("/api/v1/users/me", aliceHeaders, String.class);

        // when
        JsonNode page = page(aliceHeaders);

        // then
        String stored = page.toString();
        assertThat(stored).doesNotContain(alice.getPassword(), aliceToken, "reset-token-value", "BrandNew123!");
        JsonNode signIn = record(page, "/api/v1/users/signin");
        assertThat(signIn.at("/requestBody/password").asString()).isEqualTo("***");
        assertThat(signIn.at("/responseBody/token").asString()).isEqualTo("***");
        assertThat(signIn.at("/responseBody/refreshToken").asString()).isEqualTo("***");
        assertThat(record(page, "/api/v1/users/password/reset").at("/requestBody/newPassword").asString())
                .isEqualTo("***");
        JsonNode meHeaders = record(page, "/api/v1/users/me").path("requestHeaders");
        assertThat(meHeaders.propertyNames().stream().filter(name -> name.equalsIgnoreCase("authorization"))
                .map(name -> meHeaders.path(name).path(0).asString()).toList()).containsExactly("***");
    }

    @Test
    void anonymousReadsStillRequireAuthenticationWithoutLegacyPublicAccess() {
        HttpHeaders anonymous = withSession(getJsonOnlyHeaders(), "anonymous-" + UUID.randomUUID());

        assertThat(executeGet(LOGS, anonymous, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String signIn(String username, String password, String session) {
        return executePost(LOGIN_ENDPOINT, LoginDto.builder().username(username).password(password).build(),
                withSession(getJsonOnlyHeaders(), session), String.class).getBody();
    }

    private JsonNode page(HttpHeaders headers) {
        var response = executeGet(LOGS + "?size=50", headers, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }

    private static List<String> paths(JsonNode page) {
        return page.path("content").valueStream()
                .map(entry -> entry.path("method").asString() + " " + entry.path("path").asString() + " "
                        + entry.path("status").asInt())
                .toList();
    }

    private static JsonNode record(JsonNode page, String path) {
        return page.path("content").valueStream()
                .filter(entry -> entry.path("path").asString().equals(path))
                .findFirst()
                .orElseThrow();
    }

    private static HttpHeaders withSession(HttpHeaders headers, String session) {
        headers.set(TrafficSession.HEADER, session);
        return headers;
    }
}
