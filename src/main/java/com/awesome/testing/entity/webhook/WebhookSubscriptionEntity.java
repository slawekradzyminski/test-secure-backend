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
import java.util.Arrays;
import java.util.List;

/** A user's webhook endpoint and the event types it receives. */
@Entity
@Table(name = "webhook_subscriptions",
        indexes = @Index(name = "idx_webhook_subscriptions_username", columnList = "username"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 255)
    private String description;

    /** Comma-separated event wire names; use {@link #eventList()} and {@link #setEventList(List)}. */
    @Column(nullable = false, length = 1000)
    private String events;

    /** Signing secret, encrypted at rest. */
    @Column(nullable = false, length = 512)
    private String secret;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public List<String> eventList() {
        if (events == null || events.isBlank()) {
            return List.of();
        }
        return Arrays.stream(events.split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList();
    }

    public void setEventList(List<String> names) {
        this.events = String.join(",", names);
    }

    public boolean subscribesTo(String eventType) {
        return eventList().contains(eventType);
    }
}
