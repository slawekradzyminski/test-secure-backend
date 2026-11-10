package com.awesome.testing.repository.webhook;

import com.awesome.testing.dto.webhook.WebhookDeliveryStatus;
import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, UUID> {

    Page<WebhookDeliveryEntity> findBySubscriptionId(Long subscriptionId, Pageable pageable);

    Page<WebhookDeliveryEntity> findBySubscriptionIdAndStatus(Long subscriptionId, WebhookDeliveryStatus status,
                                                              Pageable pageable);

    Optional<WebhookDeliveryEntity> findByIdAndSubscriptionId(UUID id, Long subscriptionId);

    /** PENDING deliveries whose next attempt is due, oldest first. */
    @Query("""
            SELECT d.id FROM WebhookDeliveryEntity d
            WHERE d.status = :pending AND d.nextAttemptAt <= :now
            ORDER BY d.nextAttemptAt, d.createdAt""")
    List<UUID> findDueIds(@Param("pending") WebhookDeliveryStatus pending, @Param("now") Instant now, Pageable pageable);

    /**
     * Claims a due PENDING delivery for one attempt by moving its next attempt past
     * {@code leaseUntil}. Returns 1 when this caller won the claim.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WebhookDeliveryEntity d SET d.nextAttemptAt = :leaseUntil
            WHERE d.id = :id AND d.status = :pending AND d.nextAttemptAt <= :now""")
    int claim(@Param("id") UUID id, @Param("pending") WebhookDeliveryStatus pending, @Param("now") Instant now,
              @Param("leaseUntil") Instant leaseUntil);

    @Modifying
    @Query("DELETE FROM WebhookDeliveryEntity d WHERE d.subscriptionId IN :subscriptionIds")
    void deleteBySubscriptionIds(@Param("subscriptionIds") Collection<Long> subscriptionIds);
}
