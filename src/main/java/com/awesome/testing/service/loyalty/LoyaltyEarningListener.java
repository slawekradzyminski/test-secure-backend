package com.awesome.testing.service.loyalty;

import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Credits loyalty points once an order is paid, after the paying transaction
 * committed. Earning is idempotent per order, so a repeated ORDER_PAID (an
 * order set back to PENDING and paid again) credits nothing. A failure is
 * logged and never breaks the request that paid the order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyEarningListener {

    private final LoyaltyService loyaltyService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(CommerceEvent event) {
        if (event.type() != CommerceEventType.ORDER_PAID || event.orderId() == null) {
            return;
        }
        try {
            loyaltyService.earnForOrder(event.orderId());
        } catch (RuntimeException e) {
            log.error("Could not credit loyalty points for order {}", event.orderId(), e);
        }
    }
}
