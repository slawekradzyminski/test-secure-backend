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

        assertThat(wireNames).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry(CommerceEventType.ORDER_CREATED, "order.created"),
                Map.entry(CommerceEventType.ORDER_PAID, "order.paid"),
                Map.entry(CommerceEventType.ORDER_CANCELLED, "order.cancelled"),
                Map.entry(CommerceEventType.ORDER_SHIPPED, "shipment.shipped"),
                Map.entry(CommerceEventType.ORDER_DELIVERED, "shipment.delivered"),
                Map.entry(CommerceEventType.REFUND_BOOKED, "refund.booked"),
                Map.entry(CommerceEventType.PAYMENT_FAILED, "payment.failed"),
                Map.entry(CommerceEventType.CARD_REFUND_REQUESTED, "card-refund.requested"),
                Map.entry(CommerceEventType.ORDER_RESERVATION_EXPIRED, "order.reservation-expired"),
                Map.entry(CommerceEventType.NOTIFICATION_REQUESTED, "notification.requested"),
                Map.entry(CommerceEventType.STOCK_RESTORE_REQUESTED, "stock.restore-requested")));
    }

    @Test
    void onlyTheInternalServiceEventsAreKeptFromWebhooks() {
        assertThat(Arrays.stream(CommerceEventType.values()).filter(type -> !type.offeredToWebhooks()))
                .containsExactly(CommerceEventType.CARD_REFUND_REQUESTED, CommerceEventType.NOTIFICATION_REQUESTED,
                        CommerceEventType.STOCK_RESTORE_REQUESTED);
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
