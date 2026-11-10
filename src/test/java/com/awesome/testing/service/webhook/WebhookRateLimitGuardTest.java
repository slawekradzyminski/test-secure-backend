package com.awesome.testing.service.webhook;

import com.awesome.testing.security.ratelimit.RateLimitProperties;
import com.awesome.testing.security.ratelimit.RateLimitService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebhookRateLimitGuardTest {

    private final RateLimitService service = mock(RateLimitService.class);
    private final RateLimitProperties properties = new RateLimitProperties();
    private final WebhookRateLimitGuard guard = new WebhookRateLimitGuard(service, properties);

    @Test
    void limitsTestPingsPerNormalizedUser() {
        guard.checkTest(" Alice ");
        verify(service).check("/api/v1/webhooks/test", "username", "alice", properties.getPolicies().getWebhookTestUser());
    }

    @Test
    void limitsRedeliveriesPerNormalizedUser() {
        guard.checkRedeliver("BOB");
        verify(service).check("/api/v1/webhooks/redeliver", "username", "bob",
                properties.getPolicies().getWebhookRedeliverUser());
    }
}
