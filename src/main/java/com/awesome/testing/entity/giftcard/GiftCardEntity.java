package com.awesome.testing.entity.giftcard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A bearer gift card. The balance changes only together with a
 * {@link GiftCardTransactionEntity} ledger row and only under a pessimistic lock.
 */
@Entity
@Table(name = "gift_cards")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 19)
    private String code;

    @Column(name = "initial_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balance;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Set when an administrator disables the card; a disabled card cannot be used again. */
    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "recipient_username")
    private String recipientUsername;

    @Column
    private String note;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
