package com.awesome.testing.event.broker;

import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.MessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Outbox relay of the broker transport (docs/EVENT_BROKER_CONTRACT.md in awesome-localstack). With
 * {@code events.transport=broker} it sends every {@code domain_events} row that has no
 * {@code published_at} to the Artemis multicast address {@code events.broker.address}, in sequence
 * order, then sets {@code published_at}:
 * <ol>
 *   <li>a short read-only transaction loads up to {@value #BATCH_SIZE} unpublished rows;</li>
 *   <li>one transacted JMS session sends them, with no database connection held (pool size 3);</li>
 *   <li>a second short transaction marks them published.</li>
 * </ol>
 * Delivery is at least once: a crash between 2 and 3 sends the batch again, and the broker's duplicate
 * detection ({@code _AMQ_DUPL_ID}, on unless {@code events.broker.duplicate-detection=false}) or the
 * consumers' {@code processed_events} drop the copy. Rows recorded more than {@code events.broker.max-age}
 * ago are marked published without being sent. While the broker is unreachable the relay backs off from
 * one second to {@value #MAX_BACKOFF_SECONDS} seconds and logs once per outage; requests are not affected.
 *
 * <p>With tracing on each event is sent in a {@code publish <type>} PRODUCER span whose parent is the
 * request that wrote the event; its {@code traceparent} travels as a message property, so the consumer's
 * {@code process <type>} span is its child.
 */
@Slf4j
@Component
public class EventRelay {

    static final int BATCH_SIZE = 100;
    /** Upper bound of batches per run, so a long backlog does not monopolise a scheduler thread. */
    static final int MAX_BATCHES_PER_RUN = 20;
    static final long MAX_BACKOFF_SECONDS = 30;
    static final String SPAN_PREFIX = "publish ";

    private final DomainEventRepository repository;
    private final TransactionTemplate readOnly;
    private final JmsTemplate jms;
    private final CorrelationTracing tracing;
    private final Clock commerceClock;
    private final EventTransport transport;
    private final String address;
    private final Duration maxAge;
    private final boolean duplicateDetection;
    private final AtomicLong pending = new AtomicLong();
    private final Counter published;
    private final Counter skipped;
    private final Counter failures;

    private Duration backoff = Duration.ZERO;
    private Instant nextAttempt = Instant.MIN;
    private boolean failing;

    public EventRelay(DomainEventRepository repository, PlatformTransactionManager transactionManager,
                      ConnectionFactory connectionFactory, CorrelationTracing tracing, Clock commerceClock,
                      MeterRegistry meterRegistry,
                      @Value("${events.transport:feed}") String transport,
                      @Value("${events.broker.address:awesome.commerce.events}") String address,
                      @Value("${events.broker.max-age:PT24H}") Duration maxAge,
                      @Value("${events.broker.duplicate-detection:true}") boolean duplicateDetection) {
        this.repository = repository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        // A plain template on the connection factory: no caching proxies (native image), and the email
        // template's local-outbox switch must not apply to events.
        this.jms = new JmsTemplate(connectionFactory);
        this.jms.setSessionTransacted(true);
        this.tracing = tracing;
        this.commerceClock = commerceClock;
        this.transport = EventTransport.from(transport);
        this.address = address;
        this.maxAge = maxAge;
        this.duplicateDetection = duplicateDetection;
        Gauge.builder("events.relay.pending", pending, AtomicLong::get)
                .description("Domain events not yet published to the broker (broker transport)")
                .register(meterRegistry);
        this.published = Counter.builder("events.relay.published")
                .description("Domain events published to the broker").register(meterRegistry);
        this.skipped = Counter.builder("events.relay.skipped")
                .description("Unpublished domain events older than events.broker.max-age, marked without sending")
                .register(meterRegistry);
        this.failures = Counter.builder("events.relay.failures")
                .description("Relay runs that could not reach the broker or the database").register(meterRegistry);
    }

    public EventTransport getTransport() {
        return transport;
    }

    /** The backlog seen by the last run. */
    public long getPending() {
        return pending.get();
    }

    @Scheduled(fixedDelayString = "${events.broker.relay-interval:PT1S}",
            initialDelayString = "${events.broker.relay-initial-delay:PT5S}")
    public void scheduledRelay() {
        if (transport == EventTransport.BROKER) {
            relay();
        }
    }

    /**
     * Publishes the backlog (up to {@value #MAX_BATCHES_PER_RUN} batches) unless the relay is backing off.
     *
     * @return the number of events sent to the broker in this run
     */
    public synchronized int relay() {
        if (commerceClock.instant().isBefore(nextAttempt)) {
            return 0;
        }
        int sent = 0;
        try {
            for (int batchNumber = 0; batchNumber < MAX_BATCHES_PER_RUN; batchNumber++) {
                List<DomainEventEntity> batch = readOnly.execute(status -> {
                    pending.set(repository.countUnpublished());
                    return repository.findUnpublished(PageRequest.of(0, BATCH_SIZE));
                });
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                sent += publish(batch);
                pending.set(Math.max(0, pending.get() - batch.size()));
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
        } catch (JmsException e) {
            fail("broker", e);
            return sent;
        } catch (DataAccessException e) {
            fail("database", e);
            return sent;
        }
        recover();
        return sent;
    }

    private int publish(List<DomainEventEntity> batch) {
        Instant cutoff = commerceClock.instant().minus(maxAge);
        List<DomainEventEntity> stale = new ArrayList<>();
        List<DomainEventEntity> fresh = new ArrayList<>();
        for (DomainEventEntity event : batch) {
            (event.getRecordedAt().isBefore(cutoff) ? stale : fresh).add(event);
        }
        if (!stale.isEmpty()) {
            repository.markPublished(sequences(stale), commerceClock.instant());
            skipped.increment(stale.size());
            log.warn("Marked {} event(s) recorded before {} as published without sending them (events.broker.max-age"
                    + " {}); consumers recover them from the feed", stale.size(), cutoff, maxAge);
        }
        if (fresh.isEmpty()) {
            return 0;
        }
        send(fresh);
        repository.markPublished(sequences(fresh), commerceClock.instant());
        published.increment(fresh.size());
        log.debug("Published {} event(s) up to #{} to {}", fresh.size(), fresh.getLast().getSequence(), address);
        return fresh.size();
    }

    private void send(List<DomainEventEntity> events) {
        jms.execute(session -> {
            try (MessageProducer producer = session.createProducer(session.createTopic(address))) {
                for (DomainEventEntity event : events) {
                    try (CorrelationIds.Scope ignored = tracing.resume(SPAN_PREFIX + event.getType(),
                            event.getRequestId(), storedTraceparent(event))) {
                        producer.send(EventMessages.toMessage(session, event, tracing.currentTraceparent(),
                                duplicateDetection));
                    }
                }
            }
            if (session.getTransacted()) {
                session.commit();
            }
            return null;
        }, true);
    }

    /** The writing request's span as a {@code traceparent}, or null when the event has no trace. */
    static String storedTraceparent(DomainEventEntity event) {
        String traceId = event.getTraceId();
        String spanId = event.getSpanId();
        if (traceId == null || spanId == null || traceId.length() != 32 || spanId.length() != 16) {
            return null;
        }
        return "00-" + traceId + "-" + spanId + "-01";
    }

    private static List<Long> sequences(List<DomainEventEntity> events) {
        return events.stream().map(DomainEventEntity::getSequence).toList();
    }

    private void fail(String what, RuntimeException e) {
        failures.increment();
        backoff = backoff.isZero() ? Duration.ofSeconds(1)
                : backoff.multipliedBy(2).compareTo(Duration.ofSeconds(MAX_BACKOFF_SECONDS)) > 0
                ? Duration.ofSeconds(MAX_BACKOFF_SECONDS) : backoff.multipliedBy(2);
        nextAttempt = commerceClock.instant().plus(backoff);
        if (failing) {
            log.debug("Event relay still failing ({}); next attempt in {}", e.getMessage(), backoff);
        } else {
            failing = true;
            log.warn("Event relay cannot reach the {} ({}); {} event(s) wait, retrying with backoff up to {} s",
                    what, e.getMessage(), pending.get(), MAX_BACKOFF_SECONDS);
        }
    }

    private void recover() {
        if (failing) {
            log.info("Event relay reached the broker again; {} event(s) still pending", pending.get());
        }
        failing = false;
        backoff = Duration.ZERO;
        nextAttempt = Instant.MIN;
    }
}
