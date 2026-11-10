package com.awesome.testing.service.webhook;

import com.awesome.testing.security.ratelimit.RateLimitProperties;
import com.awesome.testing.security.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Per-user limits for the webhook actions that make outbound requests on demand. */
@Component
@RequiredArgsConstructor
public class WebhookRateLimitGuard {

    static final String TEST_ENDPOINT = "/api/v1/webhooks/test";
    static final String REDELIVER_ENDPOINT = "/api/v1/webhooks/redeliver";

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    public void checkTest(String username) {
        rateLimitService.check(TEST_ENDPOINT, "username", normalize(username),
                properties.getPolicies().getWebhookTestUser());
    }

    public void checkRedeliver(String username) {
        rateLimitService.check(REDELIVER_ENDPOINT, "username", normalize(username),
                properties.getPolicies().getWebhookRedeliverUser());
    }

    private static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
