package com.awesome.testing.repository;

import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    List<ConversationEntity> findByOwnerUsernameAndArchivedFalseOrderByUpdatedAtDesc(String username);

    List<ConversationEntity> findByOwnerUsernameAndTypeAndArchivedFalseOrderByUpdatedAtDesc(
            String username,
            ConversationType type);

    Optional<ConversationEntity> findByIdAndOwnerUsernameAndArchivedFalse(UUID id, String username);
}
