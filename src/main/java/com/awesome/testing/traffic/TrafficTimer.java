package com.awesome.testing.traffic;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Monotonic request timing, independent of wall-clock adjustments. */
@Component
public class TrafficTimer {
    private final LongSupplier nanos;

    public TrafficTimer() {
        this(System::nanoTime);
    }

    TrafficTimer(LongSupplier nanos) {
        this.nanos = nanos;
    }

    public long start() {
        return nanos.getAsLong();
    }

    public long elapsedMillis(long start) {
        return Duration.ofNanos(nanos.getAsLong() - start).toMillis();
    }
}
