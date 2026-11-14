package com.awesome.testing.service.webhook;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Settings of user webhook subscriptions ({@code webhooks.*}). */
@ConfigurationProperties(prefix = "webhooks")
@Getter
@Setter
public class WebhookProperties {

    /**
     * False where the notification-service owns webhooks: no delivery rows are recorded and no
     * delivery is sent or retried; the endpoints stay mapped. Checked at runtime (one native bean graph).
     */
    private boolean enabled = true;
    /** Allow plain http targets (https is always allowed). The SSRF guard applies either way. */
    private boolean allowHttp = true;
    /** Target ports allowed in addition to 80 and 443. */
    private List<Integer> allowedPorts = new ArrayList<>();
    /** Connect plus response deadline of one attempt; only a 2xx within it counts as delivered. */
    private Duration timeout = Duration.ofSeconds(5);
    /** Attempts per delivery before it stays FAILED. */
    private int maxAttempts = 8;
    /** Delay before the first retry; it doubles with every further failed attempt. */
    private Duration initialBackoff = Duration.ofMinutes(1);
    /** Consecutive failed attempts after which a subscription is disabled. */
    private int autoDisableAfter = 20;
    private int maxSubscriptionsPerUser = 5;
    private int maxInboxesPerUser = 3;
    /** Requests kept per inbox (newest first). */
    private int inboxRequestLimit = 50;
    /** Send event deliveries on a background executor (true) or in the committing thread (tests). */
    private boolean asyncDispatch = true;
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Retry {
        /** Run {@link WebhookRetryJob} on its schedule. */
        private boolean enabled = true;
        private Duration interval = Duration.ofSeconds(30);
        private Duration initialDelay = Duration.ofSeconds(30);
        private int batchSize = 20;
    }
}
