package com.awesome.testing.dto.email;

public enum EmailTemplate {
    GENERIC,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_CONFIRMED,
    BACK_IN_STOCK,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    RETURN_UPDATED,
    PAYMENT_RECEIVED,
    PAYMENT_FAILED,
    SHIPMENT_OUT_FOR_DELIVERY,
    SHIPMENT_DELIVERY_FAILED,
    LOYALTY_POINTS_EARNED,
    GIFT_CARD_ISSUED

    // The backend renders every subject and body; the template travels with the email
    // as metadata, and consumers deliver unknown templates as GENERIC.
}
