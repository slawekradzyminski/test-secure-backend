package com.awesome.testing.entity.stock;

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A stock command for warehouse-service written in the transaction of the product change it belongs
 * to (table {@code stock_command_outbox}, V25) and sent after commit by
 * {@link com.awesome.testing.service.stock.StockCommandDispatcher}.
 */
@Entity
@Table(name = "stock_command_outbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCommandEntity {

    public enum Command {
        /** {@code PUT /internal/v1/stock/items/{productId}}. */
        INITIAL_STOCK,
        /** {@code DELETE /internal/v1/stock/items/{productId}}. */
        DELETE
    }

    public enum Status {
        PENDING,
        DONE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Command command;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "initial_quantity")
    private Integer initialQuantity;

    private String actor;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "correlation_request_id", length = 128)
    private String correlationRequestId;

    @Column(length = 55)
    private String traceparent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
