package com.awesome.testing.service.reservation;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Cancels PENDING orders whose stock reservation has run out ({@code reservation_expires_at <= now}).
 * Each run reads up to {@value #BATCH_SIZE} due orders, earliest first, and expires each one in its own
 * transaction through {@link OrderService#expireIfDue}, which re-checks the order under
 * {@code FOR UPDATE SKIP LOCKED}. Several backend instances can therefore run the job at the same time:
 * an order is cancelled exactly once, an order held by a capture or a customer cancel is skipped, and an
 * order that fails is logged and retried on the next run without blocking the rest of the batch.
 * {@code orders.reservation.expiry-job.enabled=false} switches the schedule off (tests call
 * {@link #expireDue()} directly).
 */
@Slf4j
@Component
public class ReservationExpiryJob {

    static final int BATCH_SIZE = 50;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock commerceClock;
    private final boolean enabled;

    public ReservationExpiryJob(OrderRepository orderRepository, OrderService orderService, Clock commerceClock,
                                @Value("${orders.reservation.expiry-job.enabled:true}") boolean enabled) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.commerceClock = commerceClock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${orders.reservation.expiry-job.interval:PT15S}",
            initialDelayString = "${orders.reservation.expiry-job.initial-delay:PT15S}")
    public void scheduledExpiry() {
        if (enabled) {
            expireDue();
        }
    }

    /** Expires one batch of due orders now, in the calling thread; returns how many this call cancelled. */
    public int expireDue() {
        List<Long> due = orderRepository.findExpiredReservationIds(
                OrderStatus.PENDING, commerceClock.instant(), PageRequest.of(0, BATCH_SIZE));
        int expired = 0;
        for (Long orderId : due) {
            try {
                if (orderService.expireIfDue(orderId)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire the reservation of order {}; retrying on the next run", orderId, e);
            }
        }
        if (expired > 0) {
            log.info("Expired the stock reservation of {} order(s)", expired);
        }
        return expired;
    }
}
