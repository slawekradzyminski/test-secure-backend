package com.awesome.testing.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.repository.TrafficLogRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;

class TrafficLogBufferTest {
    private final TrafficLogRepository repository = mock(TrafficLogRepository.class);
    private final TrafficProperties properties = new TrafficProperties();
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    @Test
    void requestThreadEnqueuesAndFlushPersistsBoundedBatchesInOrder() {
        properties.setPersistenceBatchSize(1);
        var service = new TrafficLogBuffer(repository, properties, metrics);
        var first = TrafficLogEntity.builder().correlationId("first").build();
        var second = TrafficLogEntity.builder().correlationId("second").build();
        service.enqueue(first);
        service.enqueue(second);
        verifyNoInteractions(repository);

        service.flush();
        verify(repository).saveAll(argThat(rows -> ((List<TrafficLogEntity>) rows).getFirst() == first));
        assertThat(metrics.get("traffic.persistence.pending").gauge().value()).isEqualTo(1);
        service.drainOnShutdown();
        verify(repository).saveAll(argThat(rows -> ((List<TrafficLogEntity>) rows).getFirst() == second));
        assertThat(metrics.get("traffic.persistence.pending").gauge().value()).isZero();
    }

    @Test
    void readFlushDrainsAllPreviouslyQueuedBatches() {
        properties.setPersistenceBatchSize(1);
        var service = new TrafficLogBuffer(repository, properties, metrics);
        service.enqueue(TrafficLogEntity.builder().correlationId("one").build());
        service.enqueue(TrafficLogEntity.builder().correlationId("two").build());
        service.flushForRead();
        verify(repository, times(2)).saveAll(anyList());
        assertThat(metrics.get("traffic.persistence.pending").gauge().value()).isZero();
    }

    @Test
    void oversizedHttpMetadataCannotPoisonTheSharedPersistenceBatch() {
        var service = new TrafficLogBuffer(repository, properties, metrics);
        var record =
                TrafficLogEntity.builder()
                        .correlationId("bounded")
                        .path("/".repeat(1025))
                        .queryString("x".repeat(2001))
                        .method("M".repeat(17))
                        .requestContentType("a".repeat(256))
                        .responseContentType("b".repeat(256))
                        .build();

        service.enqueue(record);
        service.flush();

        assertThat(record.getPath()).hasSize(1024);
        assertThat(record.getQueryString()).hasSize(2000);
        assertThat(record.getMethod()).hasSize(16);
        assertThat(record.getRequestContentType()).hasSize(255);
        assertThat(record.getResponseContentType()).hasSize(255);
        verify(repository).saveAll(argThat(rows -> ((List<TrafficLogEntity>) rows).getFirst() == record));
    }

    @Test
    void nullBytesCannotPoisonTextPayloadPersistence() {
        var service = new TrafficLogBuffer(repository, properties, metrics);
        var record =
                TrafficLogEntity.builder()
                        .requestBody("a\0b")
                        .responseBody("c\0d")
                        .requestHeaders("e\0f")
                        .responseHeaders("g\0h")
                        .requestBodyOriginalLength(3)
                        .requestBodyStoredLength(3)
                        .build();

        service.enqueue(record);
        service.flush();

        assertThat(record.getRequestBody()).isEqualTo("a\u2400b");
        assertThat(record.getResponseBody()).isEqualTo("c\u2400d");
        assertThat(record.getRequestHeaders()).isEqualTo("e\u2400f");
        assertThat(record.getResponseHeaders()).isEqualTo("g\u2400h");
        assertThat(record.getRequestBodyOriginalLength()).isEqualTo(3);
        assertThat(record.getRequestBodyStoredLength()).isEqualTo(3);
        verify(repository).saveAll(argThat(rows -> ((List<TrafficLogEntity>) rows).getFirst() == record));
    }

    @Test
    void fullQueueDropsNewRecordsWithoutBlockingAndCountsLoss() {
        properties.setPersistenceQueueCapacity(1);
        var service = new TrafficLogBuffer(repository, properties, metrics);
        var accepted = TrafficLogEntity.builder().correlationId("accepted").build();
        service.enqueue(accepted);
        assertThat(metrics.get("traffic.persistence.dropped").counter().count()).isZero();
        service.enqueue(TrafficLogEntity.builder().correlationId("dropped").build());
        assertThat(metrics.get("traffic.persistence.dropped").counter().count()).isEqualTo(1);
        service.flush();
        verify(repository).saveAll(argThat(rows -> ((List<TrafficLogEntity>) rows).getFirst() == accepted));
    }

    @Test
    void failedBatchIsCountedAndDoesNotPreventLaterBatches() {
        var service = new TrafficLogBuffer(repository, properties, metrics);
        var failed = TrafficLogEntity.builder().correlationId("failed").build();
        doThrow(new IllegalStateException("PRIVATE_DATABASE_DETAILS"))
                .when(repository)
                .saveAll(argThat(rows -> ((List<TrafficLogEntity>) rows).getFirst() == failed));
        service.enqueue(failed);
        service.flush();
        assertThat(metrics.get("traffic.persistence.failed").counter().count()).isEqualTo(1);
        var next = TrafficLogEntity.builder().correlationId("next").build();
        service.enqueue(next);
        service.flush();
        verify(repository).saveAll(argThat(rows -> ((List<TrafficLogEntity>) rows).getFirst() == next));
        service.flush();
        verify(repository, times(2)).saveAll(anyList());
    }

    @Test
    void websocketQueueDropsNewEventsAndCountsLoss() {
        var queue = new BoundedTrafficQueue(1, metrics);
        var event = TrafficEventDto.builder().path("first").build();
        assertThat(queue.offer(event)).isTrue();
        assertThat(metrics.get("traffic.events.dropped").counter().count()).isZero();
        assertThat(queue.offer(TrafficEventDto.builder().path("second").build())).isFalse();
        assertThat(metrics.get("traffic.events.dropped").counter().count()).isEqualTo(1);
        assertThat(queue.poll()).isEqualTo(event);
        assertThat(queue).isEmpty();
    }
}
