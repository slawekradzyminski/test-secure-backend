package com.awesome.testing.event;

/**
 * Why an order was cancelled; sent as {@code data.reason} of {@code order.cancelled}.
 */
public enum CancellationReason {
    /** The customer cancelled their own order. */
    CUSTOMER,
    /** An administrator cancelled the order (cancel endpoint, status change or GraphQL). */
    ADMIN,
    /** The stock reservation of a PENDING order ran out ({@code ReservationExpiryJob} or the admin time control). */
    RESERVATION_EXPIRED,
    /** warehouse-service cancelled the shipment. */
    WAREHOUSE,
    /** The customer's account was erased. */
    ACCOUNT_ERASURE;

    /** Actor of changes the backend makes on its own, such as reservation expiry. */
    public static final String SYSTEM_ACTOR = "system";

    /** Actor written on stock movements: the customer, except for expiry, which the system performs. */
    public String actorFor(String username) {
        return this == RESERVATION_EXPIRED ? SYSTEM_ACTOR : username;
    }
}
