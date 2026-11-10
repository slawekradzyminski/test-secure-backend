package com.awesome.testing.entity.giftcard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One change of a gift card balance. {@code amount} is signed: positive for
 * ISSUED and RESTORED, negative for REDEEMED, zero for DISABLED. The
 * {@code reference} (order or refund request) is unique per card and type,
 * which makes redemptions and restores idempotent.
 */
@Entity
@Table(name = "gift_card_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCardTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gift_card_id", nullable = false)
    private Long giftCardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GiftCardTransactionType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 80)
    private String reference;

    @Column
    private String actor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
