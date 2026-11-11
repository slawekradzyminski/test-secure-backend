package com.awesome.testing.service.payment;

import com.awesome.testing.entity.payment.CardRefundRequestEntity;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.payment.CardRefundRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Asks payment-service to refund a card by publishing {@link CommerceEventType#CARD_REFUND_REQUESTED}
 * (recorded in the event feed that payment-service polls) and remembers the request in
 * {@code card_refund_requests}, both inside the transaction that booked the refund or cancelled the
 * order. The backend never calls payment-service; payment-service deduplicates the requestId.
 */
@Service
@RequiredArgsConstructor
public class CardRefunds {

    private final CardRefundRequestRepository repository;
    private final CommerceEvents commerceEvents;
    private final Clock commerceClock;

    /** Card amount of the order already requested back. */
    @Transactional(readOnly = true)
    public BigDecimal cardRefunded(Long orderId) {
        return repository.sumAmountByOrderId(orderId);
    }

    /** True when a card refund with this requestId was already requested. */
    @Transactional(readOnly = true)
    public boolean isRequested(UUID requestId) {
        return repository.existsById(requestId);
    }

    /** Requests a card refund; call inside the transaction that booked the refund or cancelled the order. */
    @Transactional
    public void request(Long orderId, String username, UUID paymentId, UUID requestId, BigDecimal amount,
                        String reason) {
        repository.save(CardRefundRequestEntity.builder()
                .requestId(requestId)
                .orderId(orderId)
                .paymentId(paymentId)
                .amount(amount)
                .reason(reason)
                .createdAt(commerceClock.instant())
                .build());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("orderId", orderId);
        data.put("amount", amount);
        data.put("reason", reason);
        commerceEvents.publishData(CommerceEventType.CARD_REFUND_REQUESTED, username, orderId, data);
    }
}
