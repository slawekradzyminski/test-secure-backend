package com.awesome.testing.traffic.protocol;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.traffic.TrafficLogService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProtocolTrafficRecorderTest {
    @Test
    void completionAndCancellationRacesProduceOnlyOneEvent() {
        // given
        var logs = mock(TrafficLogService.class);
        var events = new ConcurrentLinkedQueue<TrafficEventDto>();
        var recorder = new ProtocolTrafficRecorder(logs, events, JsonMapper.builder().build());
        var initial = recorder.start(UUID.randomUUID().toString());
        var trace = new ProtocolTrafficRecorder.Trace(initial.id(), initial.session(), initial.timestamp(),
                System.nanoTime() - 5_000_000, initial.completed());

        // when
        recorder.complete(trace, "GRPC", "/service/Method", 0, "Method", "SUCCESS", List.of("OK"));
        recorder.complete(trace, "GRPC", "/service/Method", 1, "Method", "ERROR", List.of("CANCELLED"));
        recorder.complete(null, "GRPC", "/service/Method", 1, "Method", "ERROR", List.of("CANCELLED"));

        // then
        verify(logs, times(1)).save(any(TrafficLogEntity.class));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getProtocolDetails().correlationId()).isEqualTo(trace.id());
            assertThat(event.getStatus()).isZero();
            assertThat(event.getDurationMs()).isBetween(0L, 1000L);
        });
    }

    @Test
    void storageFailuresCannotFailBusinessOperationsOrPublishUnretrievableEvents() {
        // given
        var logs = mock(TrafficLogService.class);
        var events = new ConcurrentLinkedQueue<TrafficEventDto>();
        var recorder = new ProtocolTrafficRecorder(logs, events, JsonMapper.builder().build());
        var trace = recorder.start(UUID.randomUUID().toString());
        doThrow(new IllegalStateException("PRIVATE_DATABASE_DETAILS")).when(logs).save(any());

        // when / then
        assertThatCode(() -> recorder.complete(trace, "GRAPHQL", "/api/v1/graphql", 200, "query cart", "SUCCESS", List.of()))
                .doesNotThrowAnyException();
        assertThat(events).isEmpty();
        assertThat(recorder.start(null)).isNull();
        assertThat(recorder.start("short")).isNull();
    }
}
