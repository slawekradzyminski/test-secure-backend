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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Keeps gift cards and loyalty points in step with the order inside the
 * transaction that changed it (a plain listener, so a failure rolls the order
 * change back too):
 * <ul>
 *   <li>ORDER_CREATED: writes the REDEEMED ledger rows, now that the order has an id.</li>
 *   <li>ORDER_CANCELLED ({@code releaseAndCancel}): restores the gift card and points
 *       and revokes the points the order earned.</li>
 *   <li>REFUND_BOOKED: applies the default {@link RefundLegs} split; the part the card
 *       does not cover goes back to the gift card, then points, and earned points are
 *       revoked in proportion to the refunded share. Idempotent per refund requestId,
 *       so a {@code RefundSplitter} may call {@link TenderRefundService} with the same
 *       requestId as well.</li>
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
            case REFUND_BOOKED -> onRefund(order(event), (BigDecimal) event.data().get("amount"),
                    (UUID) event.data().get("requestId"));
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

    private void onRefund(OrderEntity order, BigDecimal amount, UUID requestId) {
        BigDecimal refundedSoFar = order.getRefundedAmount();
        BigDecimal refundedBefore = refundedSoFar.subtract(amount);
        RefundLegs legs = RefundLegs.split(order, refundedBefore, amount);
        if (legs.tenders().signum() > 0) {
            tenderRefundService.refundToTenders(order, requestId, legs.tenders());
        }
        loyaltyService.revokeEarned(order, "refund:" + requestId, refundedSoFar);
    }

    private OrderEntity order(CommerceEvent event) {
        return orderRepository.findById(event.orderId()).orElseThrow();
    }
}
