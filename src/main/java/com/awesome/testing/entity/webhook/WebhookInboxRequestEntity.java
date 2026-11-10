package com.awesome.testing.entity.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A webhook request received by a built-in inbox. */
@Entity
@Table(name = "webhook_inbox_requests",
        indexes = @Index(name = "idx_webhook_inbox_requests_inbox", columnList = "inbox_id,received_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookInboxRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inbox_id", nullable = false)
    private Long inboxId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** JSON object of header name to value. */
    @Column(nullable = false, columnDefinition = "text")
    private String headers;

    @Column(nullable = false, columnDefinition = "text")
    private String body;
}
