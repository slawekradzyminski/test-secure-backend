package com.awesome.testing.entity;

import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.inventory.InventoryState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItemEntity> items = new ArrayList<>();

    /** Refunds go with the order when a user is forgotten. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<com.awesome.testing.entity.refund.OrderRefundEntity> refunds = new ArrayList<>();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(length = 32)
    private String couponCode;

    /** Sum of the refunds booked for returned items; see {@link com.awesome.testing.entity.refund.OrderRefundEntity}. */
    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /** VAT contained in the gross total, fixed at checkout; all four are null when the country has no rate. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "tax_country", length = 2)
    private String taxCountry;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "net_amount", precision = 10, scale = 2)
    private BigDecimal netAmount;

    /** Gift card used at checkout; null when the order was not paid with one. */
    @Column(name = "gift_card_code", length = 19)
    private String giftCardCode;

    /** Part of {@code totalAmount} paid with {@link #giftCardCode}. */
    @Column(name = "gift_card_amount", precision = 10, scale = 2)
    private BigDecimal giftCardAmount;

    /** Loyalty points redeemed at checkout; null when none were used. */
    @Column(name = "points_redeemed")
    private Long pointsRedeemed;

    /** Part of {@code totalAmount} paid with {@link #pointsRedeemed} (100 points = 1.00). */
    @Column(name = "points_amount", precision = 10, scale = 2)
    private BigDecimal pointsAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private InventoryState inventoryState = InventoryState.LEGACY_UNTRACKED;

    /** Warehouse progress mirrored from warehouse-service; see {@link FulfilmentState}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfilment_state", nullable = false, length = 16)
    @Builder.Default
    private FulfilmentState fulfilmentState = FulfilmentState.NONE;

    @Embedded
    private AddressEntity shippingAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void defaultAmounts() {
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
        if (refundedAmount == null) {
            refundedAmount = BigDecimal.ZERO;
        }
        if (fulfilmentState == null) {
            fulfilmentState = FulfilmentState.NONE;
        }
        if (subtotalAmount == null && totalAmount != null) {
            subtotalAmount = totalAmount.add(discountAmount);
        }
    }

    public void addItem(OrderItemEntity item) {
        items.add(item);
        item.setOrder(this);
    }

}
