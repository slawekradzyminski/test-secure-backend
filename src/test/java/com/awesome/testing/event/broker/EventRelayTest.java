package com.awesome.testing.event.broker;

import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.junit.jupiter.api.Test;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    private final DomainEventRepository repository = mock(DomainEventRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(NOW);

    @Test
    void theFeedTransportNeverRelays() {
        relay("feed").scheduledRelay();

        verifyNoInteractions(repository, connectionFactory);
    }

    @Test
    void anUnknownTransportIsRejected() {
        assertThatThrownBy(() -> relay("kafka")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'feed' or 'broker'");
    }

    @Test
    void anUnreachableBrokerLeavesTheEventsUnpublishedAndBacksOff() throws JMSException {
        // given
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.countUnpublished()).thenReturn(1L);
        when(repository.findUnpublished(any(Pageable.class))).thenReturn(List.of(event(NOW.minusSeconds(5))));
        when(connectionFactory.createConnection()).thenThrow(new JMSException("Connection refused"));
        EventRelay relay = relay("broker");

        // when
        int sent = relay.relay();
        int duringBackoff = relay.relay();

        // then
        assertThat(sent).isZero();
        assertThat(duringBackoff).isZero();
        verify(connectionFactory, times(1)).createConnection();
        verify(repository, never()).markPublished(any(), any());
        assertThat(relay.getPending()).isEqualTo(1);
        assertThat(metrics.get("events.relay.failures").counter().count()).isEqualTo(1);
        assertThat(metrics.get("events.relay.pending").gauge().value()).isEqualTo(1);

        // when the first backoff (1 s) has passed
        clock.advance(Duration.ofMillis(1001));
        relay.relay();

        // then the relay tries again and doubles its backoff
        verify(connectionFactory, times(2)).createConnection();
        clock.advance(Duration.ofMillis(1500));
        relay.relay();
        verify(connectionFactory, times(2)).createConnection();
    }

    @Test
    void aDuplicateIdRejectionIsRecognisedThroughSpringsAndTheClientsWrappers() {
        JMSException linked = new JMSException("AMQ229000");
        linked.setLinkedException(new ActiveMQDuplicateIdException());
        JMSException caused = new JMSException("AMQ229000");
        caused.initCause(new ActiveMQDuplicateIdException());
        JMSException other = new JMSException("Connection refused");
        other.setLinkedException(new ActiveMQNotConnectedException());

        assertThat(EventRelay.isDuplicateRejection(new UncategorizedJmsException(linked))).isTrue();
        assertThat(EventRelay.isDuplicateRejection(new UncategorizedJmsException(caused))).isTrue();
        assertThat(EventRelay.isDuplicateRejection(new UncategorizedJmsException(other))).isFalse();
        assertThat(EventRelay.isDuplicateRejection(new UncategorizedJmsException("no cause"))).isFalse();
        assertThat(EventRelay.isDuplicateRejection(null)).isFalse();
    }

    @Test
    void storedTraceparentNeedsBothIds() {
        DomainEventEntity traced = DomainEventEntity.builder().traceId("a".repeat(32)).spanId("b".repeat(16)).build();
        DomainEventEntity untraced = DomainEventEntity.builder().traceId("a".repeat(32)).build();

        assertThat(EventRelay.storedTraceparent(traced)).isEqualTo("00-" + "a".repeat(32) + "-" + "b".repeat(16) + "-01");
        assertThat(EventRelay.storedTraceparent(untraced)).isNull();
    }

    private EventRelay relay(String transport) {
        return new EventRelay(repository, transactions, connectionFactory, CorrelationTracing.disabled(), clock, metrics,
                transport, "awesome.commerce.events", Duration.ofHours(24), true);
    }

    private static DomainEventEntity event(Instant recordedAt) {
        return DomainEventEntity.builder().sequence(7L).eventId(UUID.randomUUID()).type("order.cancelled")
                .orderId(3L).occurredAt(recordedAt).recordedAt(recordedAt).payload("{\"orderId\":3}").build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
