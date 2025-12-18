package com.awesome.testing.service.ollama;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
final class OllamaRequestHandler {
    private final String type;
    private final String model;
    private final Instant started = Instant.now();
    private final AtomicInteger counter = new AtomicInteger(1);

    OllamaRequestHandler(String type, String model) {
        this.type = type;
        this.model = model;
        log.info("Starting {} stream for model {}", type, model);
    }

    void log(String what, String value) {
        log.info("Received {} for {} #{} ({}): {}", what, type, counter.get(), model, value);
    }

    void next() {
        counter.incrementAndGet();
    }

    void logDone(Long totalDurationNanos) {
        long seconds = totalDurationNanos != null
                ? totalDurationNanos / 1_000_000_000L
                : Duration.between(started, Instant.now()).toSeconds();

        log.info("{} completed for model {} in {} s ({} chunks)", type, model, seconds, counter.get());
    }

    void logComplete() {
        log.info("{} stream closed for model {}", type, model);
    }

    void logError(Throwable t) {
        log.warn("Error during {} for model {}: {}", type, model, t.getMessage());
        if (log.isDebugEnabled()) {
            log.debug("Stacktrace for {} {}", type, model, t);
        }
    }
}
