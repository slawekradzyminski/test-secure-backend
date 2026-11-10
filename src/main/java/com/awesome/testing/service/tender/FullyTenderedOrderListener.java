package com.awesome.testing.service.tender;

import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * An order whose gift card and points cover the whole total has nothing left
 * for a card payment. Checkout still answers with a PENDING order (the course
 * contract); once checkout has committed, this listener marks the order PAID,
 * which publishes ORDER_PAID like an admin status change. A failure is logged
 * and leaves the order PENDING for an administrator.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class FullyTenderedOrderListener {

    private final TenderSettlementService settlementService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(CommerceEvent event) {
        if (event.type() != CommerceEventType.ORDER_CREATED || event.orderId() == null) {
            return;
        }
        try {
            settlementService.settleIfFullyTendered(event.orderId());
        } catch (RuntimeException e) {
            log.error("Could not mark fully tendered order {} as paid", event.orderId(), e);
        }
    }
}
