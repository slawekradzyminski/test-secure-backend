package com.awesome.testing.entity;

import com.awesome.testing.dto.email.EmailDeliveryStatus;
import com.awesome.testing.dto.email.EmailTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "email_event")
public class EmailEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailTemplate type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailDeliveryStatus status;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /** JMS destination of the last send; null for events recorded before retries existed. */
    @Column(name = "destination")
    private String destination;

    /** JSON wire payload, kept so a failed or lost send can be retried. */
    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** Caller-supplied idempotency key (warehouse-service notifications). */
    @Column(name = "request_id", unique = true)
    private UUID requestId;

    /** X-Request-Id of the request that queued the email; retries log under it. */
    @Column(name = "correlation_request_id", length = 128)
    private String correlationRequestId;

    /** traceparent of the request that queued the email; retries continue its trace. */
    @Column(name = "traceparent", length = 55)
    private String traceparent;
}
