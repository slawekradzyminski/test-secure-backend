package com.awesome.testing.entity.fulfilment;

public enum ShipmentStatus {
    AWAITING_PICK,
    PICKED,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean isActive() {
        return this != DELIVERED && this != CANCELLED;
    }
}
