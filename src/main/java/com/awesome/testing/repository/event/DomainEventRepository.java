package com.awesome.testing.repository.event;

import com.awesome.testing.entity.event.DomainEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainEventRepository extends JpaRepository<DomainEventEntity, Long> {

    Optional<DomainEventEntity> findByEventId(UUID eventId);

    List<DomainEventEntity> findByOrderIdOrderBySequenceAsc(Long orderId);

    @Query("SELECT COALESCE(MAX(e.sequence), 0) FROM DomainEventEntity e")
    long maxSequence();

    @Query("""
            SELECT e FROM DomainEventEntity e
            WHERE e.sequence > :after AND e.recordedAt <= :settledBefore
            ORDER BY e.sequence ASC
            """)
    List<DomainEventEntity> findSettled(@Param("after") long after,
                                        @Param("settledBefore") Instant settledBefore,
                                        Pageable pageable);

    @Query("""
            SELECT e FROM DomainEventEntity e
            WHERE e.sequence > :after AND e.recordedAt <= :settledBefore AND e.type IN :types
            ORDER BY e.sequence ASC
            """)
    List<DomainEventEntity> findSettledOfTypes(@Param("after") long after,
                                               @Param("settledBefore") Instant settledBefore,
                                               @Param("types") Collection<String> types,
                                               Pageable pageable);

    /** Runs in its own transaction when called outside one (the scheduled retention run calls it that way). */
    @Transactional
    @Modifying
    @Query("DELETE FROM DomainEventEntity e WHERE e.recordedAt < :cutoff")
    int deleteRecordedBefore(@Param("cutoff") Instant cutoff);
}
