package com.awesome.testing.service.webhook;

import com.awesome.testing.dto.webhook.WebhookDeliveryStatus;
import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import com.awesome.testing.entity.webhook.WebhookSubscriptionEntity;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.repository.webhook.WebhookDeliveryRepository;
import com.awesome.testing.repository.webhook.WebhookSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookDeliveryLogTest {

    private static final Instant NOW = Instant.parse("2026-11-10T20:40:33Z");

    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final WebhookSubscriptionRepository subscriptions = mock(WebhookSubscriptionRepository.class);
    private final WebhookSecrets secrets = mock(WebhookSecrets.class);
    private final WebhookProperties properties = new WebhookProperties();
    private WebhookDeliveryLog log;
    private WebhookSubscriptionEntity subscription;
    private WebhookDeliveryEntity delivery;

    @BeforeEach
    void setUp() {
        log = new WebhookDeliveryLog(deliveries, subscriptions, secrets, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        subscription = WebhookSubscriptionEntity.builder().id(7L).username("alice").url("https://hooks.example.com/")
                .secret("encrypted").active(true).consecutiveFailures(0).build();
        subscription.setEventList(List.of("order.created"));
        delivery = WebhookDeliveryEntity.builder().id(UUID.randomUUID()).subscriptionId(7L)
                .eventType("order.created").payload("{}").status(WebhookDeliveryStatus.PENDING).attempts(0)
                .nextAttemptAt(NOW).createdAt(NOW).build();
        when(deliveries.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(subscriptions.findById(7L)).thenReturn(Optional.of(subscription));
        when(deliveries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,2", "3,4", "4,8", "7,64"})
    void backoffStartsAtOneMinuteAndDoubles(int attempts, long minutes) {
        assertThat(WebhookDeliveryLog.backoff(attempts, Duration.ofMinutes(1))).isEqualTo(Duration.ofMinutes(minutes));
    }

    @Test
    void successMarksDeliveredAndResetsTheFailureCounter() {
        // given
        subscription.setConsecutiveFailures(5);

        // when
        WebhookDeliveryEntity recorded = log.record(delivery.getId(), DeliveryOutcome.delivered(200, "ok", 12));

        // then
        assertThat(recorded).isSameAs(delivery);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getDeliveredAt()).isEqualTo(NOW);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getLastStatusCode()).isEqualTo(200);
        assertThat(delivery.getResponseSnippet()).isEqualTo("ok");
        assertThat(delivery.getDurationMs()).isEqualTo(12);
        assertThat(subscription.getConsecutiveFailures()).isZero();
    }

    @Test
    void retryableFailuresStayPendingWithDoublingBackoff() {
        // when
        WebhookDeliveryEntity recorded = log.record(delivery.getId(), DeliveryOutcome.failed(500, "HTTP 500", "oops", 3));

        // then
        assertThat(recorded).isSameAs(delivery);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(delivery.getLastError()).isEqualTo("HTTP 500");
        assertThat(delivery.getDeliveredAt()).isNull();
        assertThat(subscription.getConsecutiveFailures()).isEqualTo(1);

        // when
        log.record(delivery.getId(), DeliveryOutcome.failed(500, "HTTP 500", "oops", 3));

        // then
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(subscription.getConsecutiveFailures()).isEqualTo(2);
    }

    @Test
    void theEighthFailedAttemptIsFinal() {
        // given
        delivery.setAttempts(6);

        // when
        log.record(delivery.getId(), DeliveryOutcome.failed(500, "HTTP 500", null, 1));

        // then
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(7);

        // when
        log.record(delivery.getId(), DeliveryOutcome.failed(500, "HTTP 500", null, 1));

        // then
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(8);
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void nonRetryableFailuresEndImmediately() {
        // when
        log.record(delivery.getId(), DeliveryOutcome.rejected("Blocked: private", 0));

        // then
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getLastError()).isEqualTo("Blocked: private");
        assertThat(subscription.getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    void theTwentiethConsecutiveFailureDisablesTheSubscription() {
        // given
        subscription.setConsecutiveFailures(18);

        // when
        log.record(delivery.getId(), DeliveryOutcome.failed(503, "HTTP 503", null, 1));

        // then
        assertThat(subscription.isActive()).isTrue();
        assertThat(subscription.getDisabledReason()).isNull();

        // when
        log.record(delivery.getId(), DeliveryOutcome.failed(503, "HTTP 503", null, 1));

        // then
        assertThat(subscription.isActive()).isFalse();
        assertThat(subscription.getConsecutiveFailures()).isEqualTo(20);
        assertThat(subscription.getDisabledReason())
                .isEqualTo("Disabled after 20 consecutive failed deliveries; last error: HTTP 503");
        assertThat(subscription.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void errorsUpToTheLimitAreKept() {
        log.record(delivery.getId(), DeliveryOutcome.failed(null, "e".repeat(1_000), null, 1));
        assertThat(delivery.getLastError()).hasSize(1_000);
    }

    @Test
    void dueIdsAskForPendingDeliveriesDueNow() {
        // given
        List<UUID> due = List.of(UUID.randomUUID());
        when(deliveries.findDueIds(eq(WebhookDeliveryStatus.PENDING), eq(NOW), any())).thenReturn(due);

        // when / then
        assertThat(log.dueIds(5)).isEqualTo(due);
    }

    @Test
    void pingsAreRecordedAsPendingDeliveriesOfTheSubscription() {
        // when
        WebhookDeliveryEntity ping = log.recordPing(subscription);

        // then
        assertThat(ping.getSubscriptionId()).isEqualTo(7L);
        assertThat(ping.getEventType()).isEqualTo("webhook.ping");
        assertThat(ping.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(ping.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(ping.getPayload()).isEqualTo(WebhookPayloads.ping(ping.getEventId(), NOW, 7L));
    }

    @Test
    void longErrorsAreTruncated() {
        // when
        log.record(delivery.getId(), DeliveryOutcome.failed(null, "e".repeat(2_000), "s".repeat(900), 1));

        // then
        assertThat(delivery.getLastError()).hasSize(1_000);
        assertThat(delivery.getResponseSnippet()).hasSize(500);
    }

    @Test
    void claimHandsOutTheDecryptedSecretAndLeasesTheDelivery() {
        // given
        when(deliveries.claim(eq(delivery.getId()), eq(WebhookDeliveryStatus.PENDING), eq(NOW), any())).thenReturn(1);
        when(secrets.reveal("encrypted")).thenReturn("whsec_plain");

        // when
        Optional<WebhookDeliveryLog.Attempt> attempt = log.claim(delivery.getId());

        // then
        ArgumentCaptor<Instant> lease = ArgumentCaptor.forClass(Instant.class);
        verify(deliveries).claim(eq(delivery.getId()), eq(WebhookDeliveryStatus.PENDING), eq(NOW), lease.capture());
        assertThat(lease.getValue()).isEqualTo(NOW.plusSeconds(40));
        assertThat(attempt).contains(new WebhookDeliveryLog.Attempt(delivery.getId(), 7L, "alice",
                "https://hooks.example.com/", "whsec_plain", "order.created", "{}", null, null));
    }

    @Test
    void claimFailsWhenAnotherSenderHoldsTheDelivery() {
        // given
        when(deliveries.claim(any(), any(), any(), any())).thenReturn(0);

        // when / then
        assertThat(log.claim(delivery.getId())).isEmpty();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
    }

    @Test
    void claimEndsDeliveriesOfDisabledSubscriptions() {
        // given
        subscription.setActive(false);
        when(deliveries.claim(any(), any(), any(), any())).thenReturn(1);

        // when
        Optional<WebhookDeliveryLog.Attempt> attempt = log.claim(delivery.getId());

        // then
        assertThat(attempt).isEmpty();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getLastError()).isEqualTo("Subscription is disabled");
    }

    @Test
    void eventsCreateOneDeliveryPerMatchingSubscription() {
        // given
        WebhookSubscriptionEntity other = WebhookSubscriptionEntity.builder().id(8L).username("alice").active(true).build();
        other.setEventList(List.of("order.cancelled"));
        WebhookSubscriptionEntity both = WebhookSubscriptionEntity.builder().id(9L).username("alice").active(true).build();
        both.setEventList(List.of("order.cancelled", "order.created"));
        when(subscriptions.findByUsernameAndActiveTrueOrderByIdAsc("alice")).thenReturn(List.of(subscription, other, both));
        CommerceEvent event = new CommerceEvent(UUID.randomUUID(), CommerceEventType.ORDER_CREATED, "alice", 1L, NOW,
                Map.of("orderId", 1L, "status", "PENDING", "totalAmount", new BigDecimal("40.00")));

        // when
        List<UUID> created = log.recordEvent(event);

        // then
        ArgumentCaptor<WebhookDeliveryEntity> saved = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveries, times(2)).save(saved.capture());
        assertThat(created).hasSize(2);
        assertThat(saved.getAllValues()).extracting(WebhookDeliveryEntity::getSubscriptionId).containsExactly(7L, 9L);
        assertThat(saved.getAllValues()).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
            assertThat(row.getEventId()).isEqualTo(event.id());
            assertThat(row.getEventType()).isEqualTo("order.created");
            assertThat(row.getNextAttemptAt()).isEqualTo(NOW);
            assertThat(row.getPayload()).isEqualTo(WebhookPayloads.forEvent(event));
        });
    }

    @Test
    void eventsWithoutAnOwnerCreateNothing() {
        CommerceEvent event = new CommerceEvent(UUID.randomUUID(), CommerceEventType.ORDER_CREATED, null, 1L, NOW, Map.of());
        assertThat(log.recordEvent(event)).isEmpty();
    }

    @Test
    void redeliveriesCopyTheEventAndBody() {
        // given
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setEventId(UUID.randomUUID());

        // when
        WebhookDeliveryEntity copy = log.recordRedelivery(delivery);

        // then
        assertThat(copy.getId()).isNotEqualTo(delivery.getId());
        assertThat(copy.getEventId()).isEqualTo(delivery.getEventId());
        assertThat(copy.getPayload()).isEqualTo(delivery.getPayload());
        assertThat(copy.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(copy.getAttempts()).isZero();
    }
}
