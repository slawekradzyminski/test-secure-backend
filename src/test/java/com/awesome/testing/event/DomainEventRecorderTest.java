package com.awesome.testing.event;

import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import com.awesome.testing.correlation.CorrelationIds;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class DomainEventRecorderTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:05.123456Z");

    private final DomainEventRepository repository = mock(DomainEventRepository.class);
    private final DomainEventRecorder recorder = new DomainEventRecorder(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void saveReturnsTheRow() {
        when(repository.save(any(DomainEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void logsEachRecordedEventInTheWritingRequestsContext() {
        Logger logger =
                (Logger) LoggerFactory.getLogger(DomainEventRecorder.class);
        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        when(repository.save(any(DomainEventEntity.class))).thenAnswer(invocation -> {
            DomainEventEntity row = invocation.getArgument(0);
            ReflectionTestUtils.setField(row, "sequence", 41L);
            return row;
        });
        MDC.put(CorrelationIds.MDC_REQUEST_ID, "log-me-1");
        try {
            recorder.record(new CommerceEvent(UUID.randomUUID(), CommerceEventType.ORDER_CANCELLED, "buyer", 9L,
                    Instant.parse("2026-11-10T12:00:00Z"), Map.of("orderId", 9L)));
        } finally {
            MDC.clear();
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }

        assertThat(appender.list).singleElement().satisfies(line -> {
            assertThat(line.getFormattedMessage()).isEqualTo("Recorded order.cancelled #41 for order 9");
            assertThat(line.getMDCPropertyMap()).containsEntry("requestId", "log-me-1");
        });
    }

    @Test
    void recordsTheEventWithItsWireNameAndSortedData() {
        UUID id = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", 9L);
        data.put("amount", new BigDecimal("12.50"));
        CommerceEvent event = new CommerceEvent(id, CommerceEventType.CARD_REFUND_REQUESTED, "buyer", 9L,
                Instant.parse("2026-11-10T12:00:00.987654Z"), data);

        recorder.record(event);

        ArgumentCaptor<DomainEventEntity> saved = ArgumentCaptor.forClass(DomainEventEntity.class);
        verify(repository).save(saved.capture());
        DomainEventEntity row = saved.getValue();
        assertThat(row.getSequence()).isNull();
        assertThat(row.getEventId()).isEqualTo(id);
        assertThat(row.getType()).isEqualTo("card-refund.requested");
        assertThat(row.getOrderId()).isEqualTo(9L);
        assertThat(row.getUsername()).isEqualTo("buyer");
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-11-10T12:00:00.987Z"));
        assertThat(row.getRecordedAt()).isEqualTo(NOW);
        assertThat(row.getPayload()).isEqualTo("{\"amount\":12.50,\"orderId\":9}");
    }

    @Test
    void recordsTheRequestIdAndTraceIdOfTheWritingRequest() {
        CommerceEvent event = new CommerceEvent(UUID.randomUUID(), CommerceEventType.ORDER_PAID, "buyer", 3L, NOW, Map.of());

        try (var ignored = new CorrelationIds("pay-req-1",
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7").open()) {
            recorder.record(event);
        }
        recorder.record(event);

        ArgumentCaptor<DomainEventEntity> saved = ArgumentCaptor.forClass(DomainEventEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getRequestId()).isEqualTo("pay-req-1");
        assertThat(saved.getAllValues().get(0).getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(saved.getAllValues().get(1).getRequestId()).as("scheduled work without a request").isNull();
        assertThat(saved.getAllValues().get(1).getTraceId()).isNull();
    }

    @Test
    void eventsWithoutOrderOrUserAreRecordedToo() {
        CommerceEvent event = new CommerceEvent(UUID.randomUUID(), CommerceEventType.ORDER_CREATED, null, null,
                NOW, Map.of());

        recorder.record(event);

        ArgumentCaptor<DomainEventEntity> saved = ArgumentCaptor.forClass(DomainEventEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isNull();
        assertThat(saved.getValue().getUsername()).isNull();
        assertThat(saved.getValue().getPayload()).isEqualTo("{}");
    }
}
