package com.awesome.testing.service.webhook;

import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebhookDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-11-10T20:40:33Z");
    private static final String SECRET = "whsec_unit-test";
    private static final String BODY = "{\"id\":\"e1\",\"type\":\"order.created\"}";
    private static final UUID DELIVERY_ID = UUID.fromString("0b8f4f0e-3f43-4b8e-9d55-4a3f1c1f6a10");

    private final WebhookDeliveryLog deliveryLog = mock(WebhookDeliveryLog.class);
    private final WebhookHttpSender sender = mock(WebhookHttpSender.class);
    private final WebhookInboxService inboxes = mock(WebhookInboxService.class);
    private final StubWebhookHostResolver dns = new StubWebhookHostResolver();
    private final WebhookProperties properties = new WebhookProperties();
    private WebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties.setAsyncDispatch(false);
        dns.register("hooks.example.com", "93.184.215.14");
        dispatcher = new WebhookDispatcher(deliveryLog, new WebhookUrlPolicy(properties, dns), sender, inboxes,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        dispatcher.shutdown();
    }

    @Test
    void signsAndSendsTheStoredBodyWithWebhookHeaders() throws IOException {
        // given
        when(sender.post(any(), anyMap(), any(), any())).thenReturn(new WebhookHttpSender.Response(204, ""));

        // when
        DeliveryOutcome outcome = dispatcher.send(attempt("https://hooks.example.com/hook"));

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<WebhookUrlPolicy.Target> target = ArgumentCaptor.forClass(WebhookUrlPolicy.Target.class);
        verify(sender).post(target.capture(), headers.capture(), eq(BODY), eq(properties.getTimeout()));
        assertThat(target.getValue().host()).isEqualTo("hooks.example.com");
        assertThat(headers.getValue()).containsExactly(
                Map.entry("Content-Type", "application/json"),
                Map.entry("User-Agent", "AwesomeTesting-Webhooks/1.0"),
                Map.entry("X-Webhook-Id", DELIVERY_ID.toString()),
                Map.entry("X-Webhook-Event", "order.created"),
                Map.entry("X-Webhook-Signature", "t=" + NOW.getEpochSecond() + ",v1="
                        + HmacReference.hex(SECRET, NOW.getEpochSecond() + "." + BODY)));
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.statusCode()).isEqualTo(204);
        assertThat(outcome.responseSnippet()).isNull();
    }

    @Test
    void measuresHowLongTheAttemptTook() throws IOException {
        // given
        when(sender.post(any(), anyMap(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(30);
            return new WebhookHttpSender.Response(200, "ok");
        });

        // when
        DeliveryOutcome outcome = dispatcher.send(attempt("https://hooks.example.com/hook"));

        // then
        assertThat(outcome.durationMs()).isBetween(30L, 5_000L);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "200|true|",
            "299|true|",
            "199|false|HTTP 199",
            "300|false|HTTP 300 (redirects are not followed)",
            "302|false|HTTP 302 (redirects are not followed)",
            "399|false|HTTP 399 (redirects are not followed)",
            "400|false|HTTP 400",
            "410|false|HTTP 410",
            "503|false|HTTP 503"})
    void onlyTwoHundredsCountAsDelivered(int status, boolean success, String error) throws IOException {
        // given
        when(sender.post(any(), anyMap(), any(), any())).thenReturn(new WebhookHttpSender.Response(status, "body"));

        // when
        DeliveryOutcome outcome = dispatcher.send(attempt("https://hooks.example.com/hook"));

        // then
        assertThat(outcome.success()).isEqualTo(success);
        assertThat(outcome.statusCode()).isEqualTo(status);
        assertThat(outcome.error()).isEqualTo(error);
        assertThat(outcome.responseSnippet()).isEqualTo("body");
        assertThat(outcome.retryable()).isEqualTo(!success);
    }

    @Test
    void reportsTimeoutsAndConnectionErrorsAsRetryableFailures() throws IOException {
        // given
        when(sender.post(any(), anyMap(), any(), any()))
                .thenThrow(new SocketTimeoutException("Read timed out"))
                .thenThrow(new ConnectException("Connection refused"))
                .thenThrow(new IOException());

        // when
        DeliveryOutcome timeout = dispatcher.send(attempt("https://hooks.example.com/hook"));
        DeliveryOutcome refused = dispatcher.send(attempt("https://hooks.example.com/hook"));
        DeliveryOutcome bare = dispatcher.send(attempt("https://hooks.example.com/hook"));

        // then
        assertThat(timeout.error()).isEqualTo("Timed out after 5000 ms");
        assertThat(timeout.retryable()).isTrue();
        assertThat(timeout.statusCode()).isNull();
        assertThat(refused.error()).isEqualTo("Request failed: ConnectException: Connection refused");
        assertThat(refused.retryable()).isTrue();
        assertThat(bare.error()).isEqualTo("Request failed: IOException");
    }

    @Test
    void blocksTargetsThatNowResolveToInternalAddressesWithoutCallingThem() {
        // given: the host passed validation earlier and now rebinds to the metadata service
        dns.register("rebind.example.com", "169.254.169.254");

        // when
        DeliveryOutcome outcome = dispatcher.send(attempt("https://rebind.example.com/hook"));

        // then
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.error()).isEqualTo("Blocked: Host rebind.example.com resolves to a link-local address; "
                + "webhooks can only call public addresses");
        verifyNoInteractions(sender);
    }

    @Test
    void deliversInboxTargetsInProcessWithTheSameSignedHeaders() {
        // given
        when(inboxes.receive(eq("tok123"), eq("alice"), anyMap(), eq(BODY))).thenReturn(true);

        // when
        DeliveryOutcome outcome = dispatcher.send(attempt("inbox://tok123"));

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(inboxes).receive(eq("tok123"), eq("alice"), headers.capture(), eq(BODY));
        assertThat(headers.getValue()).containsKeys("X-Webhook-Signature", "X-Webhook-Id", "X-Webhook-Event");
        assertThat(outcome).extracting(DeliveryOutcome::success, DeliveryOutcome::statusCode, DeliveryOutcome::responseSnippet)
                .containsExactly(true, 200, "{\"received\":true}");
        verifyNoInteractions(sender);
    }

    @Test
    void failsInboxDeliveriesWhenTheInboxIsGone() {
        // given
        when(inboxes.receive(any(), any(), anyMap(), any())).thenReturn(false);

        // when
        DeliveryOutcome outcome = dispatcher.send(attempt("inbox://gone"));

        // then
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.error()).isEqualTo("Webhook inbox not found");
    }

    @Test
    void dispatchSendsUnderTheStoredIdsOfTheQueuingRequest() throws IOException {
        // given
        String trace = "4bf92f3577b34da6a3ce929d0e0e4736";
        String stored = "00-" + trace + "-00f067aa0ba902b7-01";
        when(deliveryLog.claim(DELIVERY_ID)).thenReturn(Optional.of(new WebhookDeliveryLog.Attempt(DELIVERY_ID, 1L,
                "alice", "https://hooks.example.com/hook", SECRET, "order.created", BODY, "order-req-1", stored)));
        java.util.Map<String, String> mdcWhileSending = new java.util.HashMap<>();
        when(sender.post(any(), anyMap(), any(), any())).thenAnswer(invocation -> {
            mdcWhileSending.putAll(org.slf4j.MDC.getCopyOfContextMap());
            return new WebhookHttpSender.Response(200, "ok");
        });

        // when
        dispatcher.dispatch(DELIVERY_ID);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(sender).post(any(), headers.capture(), eq(BODY), any());
        assertThat(headers.getValue()).containsEntry("X-Request-Id", "order-req-1")
                .containsEntry("X-Webhook-Id", DELIVERY_ID.toString());
        assertThat(headers.getValue().get("traceparent")).matches("00-" + trace + "-[0-9a-f]{16}-01").isNotEqualTo(stored);
        assertThat(mdcWhileSending).containsEntry("requestId", "order-req-1").containsEntry("traceId", trace);
        assertThat(mdcWhileSending.get("spanId")).isNotEqualTo("00f067aa0ba902b7");
        assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void dispatchOfADeliveryWithoutStoredIdsSendsFreshOnes() throws IOException {
        // given
        when(deliveryLog.claim(DELIVERY_ID)).thenReturn(Optional.of(attempt("https://hooks.example.com/hook")));
        when(sender.post(any(), anyMap(), any(), any())).thenReturn(new WebhookHttpSender.Response(200, "ok"));

        // when
        dispatcher.dispatch(DELIVERY_ID);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(sender).post(any(), headers.capture(), eq(BODY), any());
        assertThat(headers.getValue().get("X-Request-Id")).hasSize(36);
        assertThat(headers.getValue().get("traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    void dispatchRecordsTheOutcomeOfAClaimedDelivery() throws IOException {
        // given
        WebhookDeliveryEntity recorded = new WebhookDeliveryEntity();
        when(deliveryLog.claim(DELIVERY_ID)).thenReturn(Optional.of(attempt("https://hooks.example.com/hook")));
        when(sender.post(any(), anyMap(), any(), any())).thenReturn(new WebhookHttpSender.Response(200, "ok"));
        when(deliveryLog.record(eq(DELIVERY_ID), any())).thenReturn(recorded);

        // when
        Optional<WebhookDeliveryEntity> result = dispatcher.dispatch(DELIVERY_ID);

        // then
        ArgumentCaptor<DeliveryOutcome> outcome = ArgumentCaptor.forClass(DeliveryOutcome.class);
        verify(deliveryLog).record(eq(DELIVERY_ID), outcome.capture());
        assertThat(outcome.getValue().success()).isTrue();
        assertThat(result).containsSame(recorded);
    }

    @Test
    void dispatchDoesNothingWhenTheDeliveryCannotBeClaimed() {
        // given
        when(deliveryLog.claim(DELIVERY_ID)).thenReturn(Optional.empty());

        // when
        Optional<WebhookDeliveryEntity> result = dispatcher.dispatch(DELIVERY_ID);

        // then
        assertThat(result).isEmpty();
        verify(deliveryLog, never()).record(any(), any());
        verifyNoInteractions(sender);
    }

    @Test
    void dispatchSwallowsUnexpectedErrors() {
        // given
        when(deliveryLog.claim(DELIVERY_ID)).thenThrow(new IllegalStateException("database down"));

        // when / then
        assertThat(dispatcher.dispatch(DELIVERY_ID)).isEmpty();
    }

    @Test
    void dispatchAllRunsInlineOrOnTheBackgroundPool() {
        // given
        UUID inline = UUID.randomUUID();
        UUID background = UUID.randomUUID();
        when(deliveryLog.claim(any())).thenReturn(Optional.empty());

        // when
        dispatcher.dispatchAll(List.of(inline));
        verify(deliveryLog).claim(inline);
        properties.setAsyncDispatch(true);
        dispatcher.dispatchAll(List.of(background));

        // then
        verify(deliveryLog, timeout(2_000)).claim(background);
    }

    private static WebhookDeliveryLog.Attempt attempt(String url) {
        return new WebhookDeliveryLog.Attempt(DELIVERY_ID, 1L, "alice", url, SECRET, "order.created", BODY, null, null);
    }
}
