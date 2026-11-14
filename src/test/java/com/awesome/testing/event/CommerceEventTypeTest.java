package com.awesome.testing.event;

import com.awesome.testing.dto.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceEventTypeTest {

    @Test
    void wireNamesFollowTheSharedContract() {
        Map<CommerceEventType, String> wireNames = Arrays.stream(CommerceEventType.values())
                .collect(Collectors.toMap(type -> type, CommerceEventType::wireName));

        assertThat(wireNames).containsExactlyInAnyOrderEntriesOf(Map.of(
                CommerceEventType.ORDER_CREATED, "order.created",
                CommerceEventType.ORDER_PAID, "order.paid",
                CommerceEventType.ORDER_CANCELLED, "order.cancelled",
                CommerceEventType.ORDER_SHIPPED, "shipment.shipped",
                CommerceEventType.ORDER_DELIVERED, "shipment.delivered",
                CommerceEventType.REFUND_BOOKED, "refund.booked",
                CommerceEventType.PAYMENT_FAILED, "payment.failed",
                CommerceEventType.CARD_REFUND_REQUESTED, "card-refund.requested",
                CommerceEventType.ORDER_RESERVATION_EXPIRED, "order.reservation-expired",
                CommerceEventType.NOTIFICATION_REQUESTED, "notification.requested"));
    }

    @Test
    void onlyTheInternalServiceEventsAreKeptFromWebhooks() {
        assertThat(Arrays.stream(CommerceEventType.values()).filter(type -> !type.offeredToWebhooks()))
                .containsExactly(CommerceEventType.CARD_REFUND_REQUESTED, CommerceEventType.NOTIFICATION_REQUESTED);
    }

    @Test
    void typesAreFoundByWireName() {
        for (CommerceEventType type : CommerceEventType.values()) {
            assertThat(CommerceEventType.fromWireName(type.wireName())).contains(type);
        }
        assertThat(CommerceEventType.fromWireName("CARD_REFUND_REQUESTED")).isEmpty();
        assertThat(CommerceEventType.fromWireName("")).isEmpty();
        assertThat(CommerceEventType.fromWireName(null)).isEmpty();
    }

    @Test
    void everyStatusExceptPendingHasAnEvent() {
        assertThat(CommerceEventType.reaching(OrderStatus.PAID)).contains(CommerceEventType.ORDER_PAID);
        assertThat(CommerceEventType.reaching(OrderStatus.SHIPPED)).contains(CommerceEventType.ORDER_SHIPPED);
        assertThat(CommerceEventType.reaching(OrderStatus.DELIVERED)).contains(CommerceEventType.ORDER_DELIVERED);
        assertThat(CommerceEventType.reaching(OrderStatus.CANCELLED)).contains(CommerceEventType.ORDER_CANCELLED);
        assertThat(CommerceEventType.reaching(OrderStatus.PENDING)).isEmpty();
    }
}
