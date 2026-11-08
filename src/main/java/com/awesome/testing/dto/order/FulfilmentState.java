package com.awesome.testing.dto.order;

/**
 * Warehouse progress of an order, mirrored from warehouse-service through the
 * internal API. The backend does not store shipments; it only keeps this state
 * so cancellation and manual status rules can be enforced locally.
 */
public enum FulfilmentState {
    NONE,
    AWAITING_PICK,
    PICKED,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /** Customers and admins may cancel the order before picking starts. */
    public boolean allowsCustomerCancellation() {
        return this == NONE || this == AWAITING_PICK;
    }

    /** The warehouse may cancel a shipment that is waiting for or has finished picking. */
    public boolean allowsWarehouseCancellation() {
        return this == AWAITING_PICK || this == PICKED;
    }

    /** While the warehouse owns the order, the legacy admin status endpoint must not change it. */
    public boolean blocksManualStatusChange() {
        return this == AWAITING_PICK || this == PICKED || this == PACKED || this == SHIPPED;
    }

    /**
     * The only forward step allowed from this state through the fulfilment
     * endpoint, or {@code null} when the state is terminal or only reachable
     * through cancellation.
     */
    public FulfilmentState nextStep() {
        return switch (this) {
            case NONE -> AWAITING_PICK;
            case AWAITING_PICK -> PICKED;
            case PICKED -> PACKED;
            case PACKED -> SHIPPED;
            case SHIPPED -> DELIVERED;
            case DELIVERED, CANCELLED -> null;
        };
    }

    /** Order status implied by reaching this state, or {@code null} when the status is unchanged. */
    public OrderStatus impliedOrderStatus() {
        return switch (this) {
            case SHIPPED -> OrderStatus.SHIPPED;
            case DELIVERED -> OrderStatus.DELIVERED;
            default -> null;
        };
    }

    /** Legacy rows written before the column existed are treated as never released to the warehouse. */
    public static FulfilmentState orNone(FulfilmentState state) {
        return state == null ? NONE : state;
    }
}
