package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.ArrayBlockingQueue;

/** Drop new telemetry events on overload, keeping request threads nonblocking. */
public final class BoundedTrafficQueue extends ArrayBlockingQueue<TrafficEventDto> {
    private static final long serialVersionUID = 1L;
    private final transient Counter dropped;

    public BoundedTrafficQueue(int capacity, MeterRegistry metrics) {
        super(capacity);
        dropped = metrics.counter("traffic.events.dropped");
        metrics.gauge("traffic.events.pending", this, BoundedTrafficQueue::size);
    }

    @Override
    public boolean offer(TrafficEventDto event) {
        boolean accepted = super.offer(event);
        if (!accepted) {
            dropped.increment();
        }
        return accepted;
    }
}
