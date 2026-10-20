package com.awesome.testing.traffic;

import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.repository.TrafficLogRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
@Slf4j
public class TrafficLogBuffer {
    private final TrafficLogRepository trafficLogRepository;
    private final BlockingQueue<TrafficLogEntity> pending;
    private final TrafficProperties properties;
    private final Counter dropped;
    private final Counter failed;

    public TrafficLogBuffer(TrafficLogRepository repository, TrafficProperties properties, MeterRegistry metrics) {
        this.trafficLogRepository = repository;
        this.properties = properties;
        this.pending = new ArrayBlockingQueue<>(properties.getPersistenceQueueCapacity());
        this.dropped = metrics.counter("traffic.persistence.dropped");
        this.failed = metrics.counter("traffic.persistence.failed");
        metrics.gauge("traffic.persistence.pending", pending, BlockingQueue::size);
    }

    /** Best effort telemetry: never perform a database write on the request thread. */
    public void enqueue(TrafficLogEntity entity) {
        // Untrusted HTTP metadata must fit the schema before it can join a shared batch.
        entity.setPath(bounded(entity.getPath(), 1024));
        entity.setQueryString(bounded(entity.getQueryString(), 2000));
        entity.setMethod(bounded(entity.getMethod(), 16));
        entity.setRequestContentType(bounded(entity.getRequestContentType(), 255));
        entity.setResponseContentType(bounded(entity.getResponseContentType(), 255));
        entity.setRequestBody(visibleNulls(entity.getRequestBody()));
        entity.setResponseBody(visibleNulls(entity.getResponseBody()));
        entity.setRequestHeaders(visibleNulls(entity.getRequestHeaders()));
        entity.setResponseHeaders(visibleNulls(entity.getResponseHeaders()));
        if (!pending.offer(entity)) {
            dropped.increment();
        }
    }

    private static String bounded(String value, int limit) {
        String preview = visibleNulls(value);
        return preview == null || preview.length() <= limit ? preview : preview.substring(0, limit);
    }

    private static String visibleNulls(String value) {
        return value == null ? null : value.replace('\0', '\u2400');
    }

    @Scheduled(fixedDelayString = "${app.traffic.persistence-flush-interval:100}")
    public synchronized void flush() {
        List<TrafficLogEntity> batch = new ArrayList<>();
        pending.drainTo(batch, properties.getPersistenceBatchSize());
        if (batch.isEmpty()) {
            return;
        }
        try {
            // saveAll commits one transaction per batch. Identity IDs still require individual INSERTs.
            trafficLogRepository.saveAll(batch);
        } catch (RuntimeException exception) {
            failed.increment(batch.size());
            log.warn("Could not persist {} traffic records; batch discarded", batch.size());
        }
    }

    /** Flush the bounded snapshot queued before a log read, preserving immediate read-back. */
    public synchronized void flushForRead() {
        int batches = Math.ceilDiv(pending.size(), properties.getPersistenceBatchSize());
        for (int i = 0; i < batches; i++) {
            flush();
        }
    }

    @PreDestroy
    public void drainOnShutdown() {
        while (!pending.isEmpty()) {
            flush();
        }
    }
}
