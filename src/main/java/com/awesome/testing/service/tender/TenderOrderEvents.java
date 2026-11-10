package com.awesome.testing.service.tender;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.service.checkout.OrderAmounts;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Keeps gift cards and loyalty points in step with the order inside the
 * transaction that changed it (a plain listener, so a failure rolls the order
 * change back too):
 * <ul>
 *   <li>ORDER_CREATED: writes the REDEEMED ledger rows, now that the order has an id.</li>
 *   <li>ORDER_CANCELLED ({@code releaseAndCancel}): restores the gift card and points
 *       and revokes the points the order earned.</li>
 *   <li>REFUND_BOOKED: revokes earned points in proportion to the refunded share of the
 *       total, idempotent per refund requestId. The money itself is split by the payments
 *       {@code CardFirstRefundSplitter}: card first, then {@link GiftCardRefundLegHandler}
 *       and {@link LoyaltyPointsRefundLegHandler}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenderOrderEvents {

    private final OrderRepository orderRepository;
    private final GiftCardService giftCardService;
    private final LoyaltyService loyaltyService;
    private final TenderRefundService tenderRefundService;

    @EventListener
    public void onCommerceEvent(CommerceEvent event) {
        switch (event.type()) {
            case ORDER_CREATED -> recordRedemptions(order(event));
            case ORDER_CANCELLED -> onCancelled(order(event));
            case REFUND_BOOKED -> onRefund(order(event), (UUID) event.data().get("requestId"));
            default -> {
                // Other events do not touch tenders.
            }
        }
    }

    private void recordRedemptions(OrderEntity order) {
        if (order.getGiftCardAmount() != null) {
            giftCardService.recordRedemption(order);
        }
        if (order.getPointsRedeemed() != null) {
            loyaltyService.recordRedemption(order);
        }
    }

    private void onCancelled(OrderEntity order) {
        if (OrderAmounts.hasTenders(order)) {
            tenderRefundService.restoreForCancellation(order);
        }
        loyaltyService.revokeEarned(order, TenderRefundService.cancellationReference(order), order.getTotalAmount());
    }

    /** Money goes back through the refund leg handlers; here only earned points are revoked. */
    private void onRefund(OrderEntity order, UUID requestId) {
        loyaltyService.revokeEarned(order, TenderRefundService.refundReference(requestId), order.getRefundedAmount());
    }

    private OrderEntity order(CommerceEvent event) {
        return orderRepository.findById(event.orderId()).orElseThrow();
    }
}
