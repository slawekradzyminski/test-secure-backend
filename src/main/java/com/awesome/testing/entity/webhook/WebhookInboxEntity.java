package com.awesome.testing.entity.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A built-in webhook receiver addressed as {@code inbox://<token>}. */
@Entity
@Table(name = "webhook_inboxes",
        uniqueConstraints = @UniqueConstraint(name = "uk_webhook_inboxes_token", columnNames = "token"),
        indexes = @Index(name = "idx_webhook_inboxes_username", columnList = "username"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookInboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(nullable = false)
    private String username;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
