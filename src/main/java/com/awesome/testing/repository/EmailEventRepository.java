package com.awesome.testing.repository;

import com.awesome.testing.dto.email.EmailDeliveryStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.UserEntity;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailEventRepository extends JpaRepository<EmailEventEntity, Integer> {

    List<EmailEventEntity> findTop20ByUserOrderByCreatedAtDesc(UserEntity user);

    long countByUserUsername(String username);

    boolean existsByRequestId(UUID requestId);

    /** Tracked emails due for another send, oldest first; see EmailEventService#claimDue. */
    @Query("""
            SELECT e FROM EmailEventEntity e
            WHERE e.payload IS NOT NULL AND e.destination IS NOT NULL
              AND ((e.status = :failed AND e.attempts < :maxAttempts AND e.nextAttemptAt <= :now)
                OR (e.status = :queued AND e.updatedAt < :staleBefore))
            ORDER BY e.id""")
    List<EmailEventEntity> findDueForRetry(@Param("failed") EmailDeliveryStatus failed,
                                           @Param("maxAttempts") int maxAttempts,
                                           @Param("now") Instant now,
                                           @Param("queued") EmailDeliveryStatus queued,
                                           @Param("staleBefore") Instant staleBefore,
                                           Pageable pageable);

    @Transactional
    void deleteAllByUser(UserEntity user);
}
