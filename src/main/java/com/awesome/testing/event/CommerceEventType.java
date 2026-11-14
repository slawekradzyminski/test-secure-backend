package com.awesome.testing.event;

import com.awesome.testing.dto.order.OrderStatus;

import java.util.Optional;

/**
 * Commerce domain events and their wire names (used by the internal event feed, webhooks and
 * other external consumers). Internal types reach only the service event feed, never user webhooks.
 */
public enum CommerceEventType {
    ORDER_CREATED("order.created", true),
    ORDER_PAID("order.paid", true),
    ORDER_CANCELLED("order.cancelled", true),
    ORDER_SHIPPED("shipment.shipped", true),
    ORDER_DELIVERED("shipment.delivered", true),
    REFUND_BOOKED("refund.booked", true),
    PAYMENT_FAILED("payment.failed", true),
    /**
     * Internal: payment-service should refund {@code amount} on the order's captured card payment.
     * {@code data} is exactly {@code requestId}, {@code orderId}, {@code amount}, {@code reason}.
     */
    CARD_REFUND_REQUESTED("card-refund.requested", false),
    /**
     * A PENDING order's stock reservation ran out and the order was cancelled (published together with
     * {@link #ORDER_CANCELLED} whose {@code reason} is {@code RESERVATION_EXPIRED}).
     * {@code data} is exactly {@code orderId}, {@code expiredAt}, {@code totalAmount}.
     */
    ORDER_RESERVATION_EXPIRED("order.reservation-expired", true),
    /**
     * Internal: the notification-service should email a customer (only recorded when
     * {@code notifications.delivery=feed}). {@code data} is exactly {@code requestId}, {@code template},
     * {@code recipientEmail}, {@code recipientName}, {@code properties}.
     */
    NOTIFICATION_REQUESTED("notification.requested", false);

    private final String wire;
    private final boolean webhookVisible;

    CommerceEventType(String wire, boolean offeredToWebhooks) {
        this.wire = wire;
        this.webhookVisible = offeredToWebhooks;
    }

    public String wireName() {
        return wire;
    }

    /** False for service-to-service events that user webhook subscriptions may not name or receive. */
    public boolean offeredToWebhooks() {
        return webhookVisible;
    }

    /** The type with this wire name, if any. */
    public static Optional<CommerceEventType> fromWireName(String wireName) {
        for (CommerceEventType type : values()) {
            if (type.wire.equals(wireName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
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
