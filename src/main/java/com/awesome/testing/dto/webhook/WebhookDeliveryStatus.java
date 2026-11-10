package com.awesome.testing.dto.webhook;

/** PENDING deliveries are waiting for their first attempt or a retry; DELIVERED and FAILED are final. */
public enum WebhookDeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED
}
