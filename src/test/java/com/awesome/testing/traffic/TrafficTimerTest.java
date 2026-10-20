package com.awesome.testing.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class TrafficTimerTest {
    @Test
    void measuresElapsedMillisecondsFromAMonotonicClock() {
        var now = new AtomicLong(100_000_000);
        var timer = new TrafficTimer(now::get);
        long started = timer.start();
        now.set(125_900_000);

        assertThat(timer.elapsedMillis(started)).isEqualTo(25);
    }
}
