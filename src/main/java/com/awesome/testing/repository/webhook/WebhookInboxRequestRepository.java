package com.awesome.testing.repository.webhook;

import com.awesome.testing.entity.webhook.WebhookInboxRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WebhookInboxRequestRepository extends JpaRepository<WebhookInboxRequestEntity, Long> {

    List<WebhookInboxRequestEntity> findByInboxIdOrderByIdDesc(Long inboxId);

    @Modifying
    @Query("DELETE FROM WebhookInboxRequestEntity r WHERE r.inboxId IN :inboxIds")
    void deleteByInboxIds(@Param("inboxIds") Collection<Long> inboxIds);
}
