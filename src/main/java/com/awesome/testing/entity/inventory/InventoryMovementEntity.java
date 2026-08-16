package com.awesome.testing.entity.inventory;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.ProductEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_movements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_product_request",
                columnNames = {"product_id", "request_id"}),
        indexes = {
                @Index(name = "idx_inventory_product_created", columnList = "product_id,created_at"),
                @Index(name = "idx_inventory_order", columnList = "order_id")
        })
@Check(name = "ck_inventory_movement_values", constraints = "delta <> 0 AND quantity_after >= 0")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryMovementType type;

    @Column(nullable = false)
    private Integer delta;

    @Column(nullable = false)
    private Integer quantityAfter;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "request_id")
    private UUID requestId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
