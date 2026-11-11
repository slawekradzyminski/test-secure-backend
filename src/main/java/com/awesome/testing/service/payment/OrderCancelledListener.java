package com.awesome.testing.service.payment;

import com.awesome.testing.entity.payment.OrderPaymentEntity;
import com.awesome.testing.entity.payment.OrderPaymentStatus;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.repository.payment.OrderPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Refunds the card when an order with a captured payment is cancelled
 * ({@code ORDER_CANCELLED} from the customer, admin or warehouse path). Runs
 * synchronously inside the cancelling transaction, so the
 * {@code card-refund.requested} feed event commits or rolls back with the
 * cancellation. The amount is the captured amount minus card refunds already
 * requested; the requestId is derived from the order id, so the cancel refund is
 * requested at most once. Orders without a captured
 * payment (paid by an admin, or never paid) get nothing; gift-card and points
 * tenders are restored by the loyalty track.
 */
@Component
@RequiredArgsConstructor
public class OrderCancelledListener {

    static final String REASON = "Order cancelled";

    private final OrderPaymentRepository paymentRepository;
    private final CardRefunds cardRefunds;

    @EventListener
    public void onOrderCancelled(CommerceEvent event) {
        if (event.type() != CommerceEventType.ORDER_CANCELLED) {
            return;
        }
        Long orderId = event.orderId();
        Optional<OrderPaymentEntity> captured =
                paymentRepository.findFirstByOrderIdAndStatus(orderId, OrderPaymentStatus.CAPTURED);
        if (captured.isEmpty()) {
            return;
        }
        UUID requestId = requestId(orderId);
        if (cardRefunds.isRequested(requestId)) {
            return;
        }
        BigDecimal refundable = captured.get().getAmount().subtract(cardRefunds.cardRefunded(orderId));
        if (refundable.signum() > 0) {
            cardRefunds.request(orderId, event.username(), captured.get().getPaymentId(), requestId, refundable, REASON);
        }
    }

    /** Deterministic requestId of the cancel refund of {@code orderId}. */
    public static UUID requestId(Long orderId) {
        return UUID.nameUUIDFromBytes(("cancel:" + orderId).getBytes(StandardCharsets.UTF_8));
    }
}
