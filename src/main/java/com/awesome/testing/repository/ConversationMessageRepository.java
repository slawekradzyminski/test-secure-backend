package com.awesome.testing.repository;

import com.awesome.testing.entity.ConversationEntity;
import com.awesome.testing.entity.ConversationMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, UUID> {
    List<ConversationMessageEntity> findByConversationOrderByPositionAsc(ConversationEntity conversation);

    long countByConversation(ConversationEntity conversation);
}
