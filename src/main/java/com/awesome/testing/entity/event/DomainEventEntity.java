package com.awesome.testing.entity.event;

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

import java.time.Instant;
import java.util.UUID;

/**
 * One commerce event in the durable feed read by warehouse-service and payment-service
 * ({@code GET /internal/v1/events}). Written in the transaction that changed the order, so a
 * rolled-back change leaves no row. {@code type} is the wire name and {@code payload} the
 * event's {@code data} as deterministic JSON (sorted keys, plain decimals).
 */
@Entity
@Table(name = "domain_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence")
    private Long sequence;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 255)
    private String username;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    /** X-Request-Id of the request that wrote the event; null for work without one (scheduled jobs). */
    @Column(name = "request_id", length = 128)
    private String requestId;

    /** W3C trace id of the writing request; null like {@link #requestId}. */
    @Column(name = "trace_id", length = 32)
    private String traceId;
}
