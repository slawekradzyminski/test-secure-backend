package com.awesome.testing.service.reservation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Time limit of the stock hold taken at checkout ({@code orders.reservation.*}). Stock is still
 * deducted when the order is created; this only decides when a PENDING order stops holding it.
 */
@Component
public class OrderReservations {

    private final boolean enabled;
    private final Duration ttl;

    public OrderReservations(@Value("${orders.reservation.enabled:true}") boolean enabled,
                             @Value("${orders.reservation.ttl:PT30M}") Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("orders.reservation.ttl must be positive, was " + ttl);
        }
        this.enabled = enabled;
        this.ttl = ttl;
    }

    /**
     * When the reservation of an order created at {@code createdAt} ends, truncated to milliseconds so the
     * stored value and every response carry the same instant; null when reservations are disabled.
     */
    public Instant expiryFor(Instant createdAt) {
        return enabled ? createdAt.plus(ttl).truncatedTo(ChronoUnit.MILLIS) : null;
    }
}
