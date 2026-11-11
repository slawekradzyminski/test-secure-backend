package com.awesome.testing.service.webhook;

import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends claimed deliveries: signs the stored body, checks the target with the SSRF guard
 * again, posts it to the vetted address (or stores it in a built-in inbox) and records the
 * outcome. Each attempt runs under the request id and trace of the request that queued the
 * delivery (MDC) and sends them as {@code X-Request-Id} and a child {@code traceparent}; the
 * delivery id stays in {@code X-Webhook-Id}. Event deliveries run on a small background pool when
 * {@code webhooks.async-dispatch} is on; a full pool leaves them PENDING for
 * {@link WebhookRetryJob}.
 */
@Slf4j
@Component
public class WebhookDispatcher {

    static final String USER_AGENT = "AwesomeTesting-Webhooks/1.0";

    private final WebhookDeliveryLog deliveryLog;
    private final WebhookUrlPolicy urlPolicy;
    private final WebhookHttpSender sender;
    private final WebhookInboxService inboxService;
    private final WebhookProperties properties;
    private final Clock clock;
    private final ThreadPoolExecutor executor;

    public WebhookDispatcher(WebhookDeliveryLog deliveryLog, WebhookUrlPolicy urlPolicy, WebhookHttpSender sender,
                             WebhookInboxService inboxService, WebhookProperties properties, Clock clock) {
        this.deliveryLog = deliveryLog;
        this.urlPolicy = urlPolicy;
        this.sender = sender;
        this.inboxService = inboxService;
        this.properties = properties;
        this.clock = clock;
        AtomicInteger threads = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200), runnable -> {
            Thread thread = new Thread(runnable, "webhook-dispatch-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Dispatches new event deliveries in the background (or inline when async dispatch is off). */
    public void dispatchAll(List<UUID> deliveryIds) {
        for (UUID id : deliveryIds) {
            if (!properties.isAsyncDispatch()) {
                dispatch(id);
                continue;
            }
            try {
                executor.execute(() -> dispatch(id));
            } catch (RejectedExecutionException e) {
                log.warn("Webhook dispatch queue is full; delivery {} waits for the retry job", id);
            }
        }
    }

    /** One attempt of a delivery if it can be claimed; failures are recorded, never thrown. */
    public Optional<WebhookDeliveryEntity> dispatch(UUID deliveryId) {
        try {
            Optional<WebhookDeliveryLog.Attempt> attempt = deliveryLog.claim(deliveryId);
            if (attempt.isEmpty()) {
                return Optional.empty();
            }
            CorrelationIds ids = CorrelationIds.resume(attempt.get().requestId(), attempt.get().traceparent());
            try (CorrelationIds.Scope ignored = ids.open()) {
                return Optional.of(deliveryLog.record(deliveryId, send(attempt.get())));
            }
        } catch (RuntimeException e) {
            log.error("Webhook delivery {} failed unexpectedly", deliveryId, e);
            return Optional.empty();
        }
    }

    DeliveryOutcome send(WebhookDeliveryLog.Attempt attempt) {
        long started = System.nanoTime();
        Map<String, String> headers = headers(attempt);
        Optional<String> inboxToken = WebhookInboxService.token(attempt.url());
        if (inboxToken.isPresent()) {
            boolean received = inboxService.receive(inboxToken.get(), attempt.username(), headers, attempt.payload());
            return received
                    ? DeliveryOutcome.delivered(200, "{\"received\":true}", elapsedMillis(started))
                    : DeliveryOutcome.rejected("Webhook inbox not found", elapsedMillis(started));
        }
        WebhookUrlPolicy.Target target;
        try {
            target = urlPolicy.check(attempt.url());
        } catch (WebhookUrlRejectedException e) {
            return DeliveryOutcome.rejected("Blocked: " + e.getMessage(), elapsedMillis(started));
        }
        try {
            WebhookHttpSender.Response response = sender.post(target, headers, attempt.payload(), properties.getTimeout());
            long duration = elapsedMillis(started);
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return DeliveryOutcome.delivered(status, response.body(), duration);
            }
            String error = status >= 300 && status < 400
                    ? "HTTP " + status + " (redirects are not followed)"
                    : "HTTP " + status;
            return DeliveryOutcome.failed(status, error, response.body(), duration);
        } catch (SocketTimeoutException e) {
            return DeliveryOutcome.failed(null, "Timed out after " + properties.getTimeout().toMillis() + " ms",
                    null, elapsedMillis(started));
        } catch (IOException e) {
            return DeliveryOutcome.failed(null, "Request failed: " + describe(e), null, elapsedMillis(started));
        }
    }

    private Map<String, String> headers(WebhookDeliveryLog.Attempt attempt) {
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", USER_AGENT);
        headers.put("X-Webhook-Id", attempt.deliveryId().toString());
        headers.put("X-Webhook-Event", attempt.eventType());
        headers.put("X-Webhook-Signature", WebhookSigner.signatureHeader(attempt.secret(), timestamp, attempt.payload()));
        CorrelationIds.current().ifPresent(ids -> {
            headers.put(CorrelationIds.REQUEST_ID_HEADER, ids.requestId());
            headers.put(CorrelationIds.TRACEPARENT_HEADER, ids.childTraceparent());
        });
        return headers;
    }

    private static String describe(IOException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
