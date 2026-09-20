package com.awesome.testing.traffic.protocol;

import com.awesome.testing.dto.traffic.ProtocolDetails;
import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.traffic.TrafficLogService;
import com.awesome.testing.traffic.TrafficSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProtocolTrafficRecorder {
    private final TrafficLogService logs;
    private final Queue<TrafficEventDto> events;
    private final ObjectMapper mapper;

    public Trace start(String session) {
        String validSession = TrafficSession.validOrNull(session);
        return validSession == null ? null : new Trace(UUID.randomUUID().toString(), validSession,
                Instant.now(), System.nanoTime(), new AtomicBoolean());
    }

    public void complete(Trace trace, String protocol, String path, int status,
                         String operation, String outcome, List<String> codes) {
        if (trace == null || !trace.completed().compareAndSet(false, true)) {
            return;
        }
        try {
            long duration = (System.nanoTime() - trace.started()) / 1_000_000;
            ProtocolDetails details = new ProtocolDetails(protocol, operation, outcome, List.copyOf(codes), trace.id());
            String summary = mapper.writeValueAsString(details);
            logs.save(TrafficLogEntity.builder().correlationId(trace.id()).clientSessionId(trace.session())
                    .timestamp(trace.timestamp()).durationMs(duration).method(protocol).path(path).status(status)
                    .requestHeaders("{}").requestContentType("application/json").requestBody("{}")
                    .requestBodyOriginalLength(2).requestBodyStoredLength(2)
                    .responseHeaders("{}").responseContentType("application/json").responseBody(summary)
                    .responseBodyOriginalLength(summary.length()).responseBodyStoredLength(summary.length()).build());
            events.offer(TrafficEventDto.builder().clientSessionId(trace.session()).method(protocol).path(path)
                    .status(status).durationMs(duration).timestamp(trace.timestamp()).protocolDetails(details).build());
        } catch (RuntimeException exception) {
            // Telemetry must neither fail an operation nor expose exception text that may contain private data.
            log.warn("Protocol traffic capture failed for correlation {}", trace.id());
        }
    }

    public record Trace(String id, String session, Instant timestamp, long started, AtomicBoolean completed) { }
}
