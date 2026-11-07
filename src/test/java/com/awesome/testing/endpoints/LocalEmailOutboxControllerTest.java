package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.controller.email.LocalEmailOutboxController;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.service.password.LocalEmailOutbox;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import static com.awesome.testing.factory.EmailFactory.getRandomEmail;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles({"test", "local"})
@TestPropertySource(properties = {
        "password-reset.require-outbox-access-key=true",
        "password-reset.outbox-access-key=" + LocalEmailOutboxControllerTest.ACCESS_KEY
})
class LocalEmailOutboxControllerTest extends DomainHelper {

    static final String ACCESS_KEY = "outbox-test-access-key";
    private static final String OUTBOX_ENDPOINT = "/api/v1/local/email/outbox";
    private static final String ACCESS_KEY_HEADER = "X-Local-Outbox-Key";

    @Autowired
    private LocalEmailOutbox localEmailOutbox;

    private String adminToken;

    @BeforeEach
    void setUp() {
        localEmailOutbox.clear();
        localEmailOutbox.store("email-queue", getRandomEmail());
        adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
    }

    @Test
    void shouldReturnOutboxToAdminWithValidAccessKey() {
        ResponseEntity<String> response = executeGet(OUTBOX_ENDPOINT, headers(adminToken, ACCESS_KEY), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("email-queue");
    }

    @Test
    void shouldRejectAdminWithoutAccessKey() {
        ResponseEntity<String> response = executeGet(OUTBOX_ENDPOINT, getHeadersWith(adminToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectAdminWithWrongAccessKey() {
        ResponseEntity<String> response = executeGet(
                OUTBOX_ENDPOINT, headers(adminToken, ACCESS_KEY + "-wrong"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectClientEvenWithValidAccessKey() {
        String clientToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        ResponseEntity<String> response = executeGet(OUTBOX_ENDPOINT, headers(clientToken, ACCESS_KEY), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectAnonymousRequestWithValidAccessKey() {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add(ACCESS_KEY_HEADER, ACCESS_KEY);

        ResponseEntity<String> response = executeGet(OUTBOX_ENDPOINT, headers, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldNotClearOutboxWithWrongAccessKey() {
        ResponseEntity<String> response = executeDelete(
                OUTBOX_ENDPOINT, headers(adminToken, ACCESS_KEY + "-wrong"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(localEmailOutbox.getAll()).hasSize(1);
    }

    @Test
    void shouldClearOutboxForAdminWithValidAccessKey() {
        ResponseEntity<String> response = executeDelete(OUTBOX_ENDPOINT, headers(adminToken, ACCESS_KEY), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(localEmailOutbox.getAll()).isEmpty();
    }

    @Test
    void shouldFailStartupWhenProtectedOutboxHasNoAccessKey() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setRequireOutboxAccessKey(true);
        properties.setOutboxAccessKey(" ");
        LocalEmailOutboxController controller = new LocalEmailOutboxController(new LocalEmailOutbox(), properties);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(controller, "validateConfiguration"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password-reset.outbox-access-key must be configured");
    }

    private HttpHeaders headers(String token, String accessKey) {
        HttpHeaders headers = getHeadersWith(token);
        headers.add(ACCESS_KEY_HEADER, accessKey);
        return headers;
    }
}
