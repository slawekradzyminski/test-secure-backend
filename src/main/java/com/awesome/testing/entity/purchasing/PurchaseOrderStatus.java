package com.awesome.testing.entity.purchasing;

public enum PurchaseOrderStatus {
    DRAFT,
    SUBMITTED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED;

    public boolean isOpenForReceipt() {
        return this == SUBMITTED || this == PARTIALLY_RECEIVED;
    }
}
