package com.awesome.testing.entity.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A card refund the backend asked payment-service for, through a
 * {@code card-refund.requested} feed event written in the same transaction. Kept
 * (unlike feed events, which expire) so later refunds know how much of the captured
 * payment is already on its way back. The database deletes rows with their order.
 */
@Entity
@Table(name = "card_refund_requests")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardRefundRequestEntity {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** The captured payment the refund goes back to. */
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
