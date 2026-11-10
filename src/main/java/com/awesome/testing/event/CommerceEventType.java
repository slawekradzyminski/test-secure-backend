package com.awesome.testing.event;

import com.awesome.testing.dto.order.OrderStatus;

import java.util.Optional;

/** Commerce domain events and their wire names (used by webhooks and other external consumers). */
public enum CommerceEventType {
    ORDER_CREATED("order.created"),
    ORDER_PAID("order.paid"),
    ORDER_CANCELLED("order.cancelled"),
    ORDER_SHIPPED("shipment.shipped"),
    ORDER_DELIVERED("shipment.delivered"),
    REFUND_BOOKED("refund.booked"),
    PAYMENT_FAILED("payment.failed");

    private final String wire;

    CommerceEventType(String wire) {
        this.wire = wire;
    }

    public String wireName() {
        return wire;
    }

    /** The event announcing that an order reached {@code status}, if that status has one. */
    public static Optional<CommerceEventType> reaching(OrderStatus status) {
        return Optional.ofNullable(switch (status) {
            case PAID -> ORDER_PAID;
            case SHIPPED -> ORDER_SHIPPED;
            case DELIVERED -> ORDER_DELIVERED;
            case CANCELLED -> ORDER_CANCELLED;
            case PENDING -> null;
        });
    }
}
