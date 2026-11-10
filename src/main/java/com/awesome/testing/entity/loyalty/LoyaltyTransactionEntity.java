package com.awesome.testing.entity.loyalty;

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

import java.time.LocalDateTime;

/**
 * One change of a loyalty balance. {@code points} is signed: positive for
 * EARNED and RESTORED, negative for REDEEMED and REVOKED. The
 * {@code reference} is unique per user and type, which makes every change idempotent.
 */
@Entity
@Table(name = "loyalty_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoyaltyTransactionType type;

    @Column(nullable = false)
    private Long points;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 80)
    private String reference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
