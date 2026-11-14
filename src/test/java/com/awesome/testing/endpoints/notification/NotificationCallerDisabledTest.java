package com.awesome.testing.endpoints.notification;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.dto.ErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A blank {@code internal.service-tokens.notification} disables the NOTIFICATION caller: the export,
 * which only it may read, does not exist (404), while the feed and JWKS keep serving the other callers.
 */
@TestPropertySource(properties = "internal.service-tokens.notification=")
class NotificationCallerDisabledTest extends HttpHelper {

    @Test
    void theExportDoesNotExistWithoutANotificationToken() {
        // when
        ResponseEntity<ErrorDto> withOldToken = executeGet(NotificationServiceCallerTest.EXPORT,
                serviceHeaders(NotificationServiceCallerTest.NOTIFICATION_TOKEN), ErrorDto.class);
        ResponseEntity<ErrorDto> withoutToken = executeGet(NotificationServiceCallerTest.EXPORT, getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(withOldToken.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(withOldToken.getBody().getMessage()).isEqualTo("Not found");
        assertThat(withoutToken.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theFeedAndJwksRejectTheOldNotificationTokenButServeTheOtherCallers() {
        // when
        ResponseEntity<ErrorDto> feed = executeGet("/internal/v1/events",
                serviceHeaders(NotificationServiceCallerTest.NOTIFICATION_TOKEN), ErrorDto.class);
        ResponseEntity<ErrorDto> jwks = executeGet("/internal/v1/jwks.json",
                serviceHeaders(NotificationServiceCallerTest.NOTIFICATION_TOKEN), ErrorDto.class);
        ResponseEntity<String> warehouseFeed = executeGet("/internal/v1/events",
                serviceHeaders("test-warehouse-token"), String.class);

        // then
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(warehouseFeed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders serviceHeaders(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", token);
        return headers;
    }
}
