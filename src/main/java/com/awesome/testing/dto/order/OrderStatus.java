package com.awesome.testing.dto.order;

public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * Order statuses only move forward: a step may be skipped, but an order is
     * never reopened. DELIVERED and CANCELLED are terminal; a new order is the
     * way to buy again. Staying in the current status is not a transition.
     */
    public boolean canMoveTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target != PENDING;
            case PAID -> target == SHIPPED || target == DELIVERED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
