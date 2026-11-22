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

    @Query("SELECT COALESCE(MAX(e.feedPosition), 0) FROM DomainEventEntity e")
    long maxFeedPosition();

    /** Whether a committed event still waits for its feed position (V29). */
    boolean existsByFeedPositionIsNull();

    /** Committed events without a feed position, oldest sequence first. */
    @Query("SELECT e FROM DomainEventEntity e WHERE e.feedPosition IS NULL ORDER BY e.sequence ASC")
    List<DomainEventEntity> findUnpositioned(Pageable pageable);

    @Query("""
            SELECT e FROM DomainEventEntity e
            WHERE e.feedPosition > :after
            ORDER BY e.feedPosition ASC
            """)
    List<DomainEventEntity> findFeed(@Param("after") long after, Pageable pageable);

    @Query("""
            SELECT e FROM DomainEventEntity e
            WHERE e.feedPosition > :after AND e.type IN :types
            ORDER BY e.feedPosition ASC
            """)
    List<DomainEventEntity> findFeedOfTypes(@Param("after") long after,
                                            @Param("types") Collection<String> types,
                                            Pageable pageable);

    /** The oldest events the outbox relay has not handed to the broker yet (broker transport). */
    @Query("SELECT e FROM DomainEventEntity e WHERE e.publishedAt IS NULL ORDER BY e.sequence ASC")
    List<DomainEventEntity> findUnpublished(Pageable pageable);

    @Query("SELECT COUNT(e) FROM DomainEventEntity e WHERE e.publishedAt IS NULL")
    long countUnpublished();

    /** Marks the relayed events published in their own transaction; rows marked meanwhile keep their time. */
    @Transactional
    @Modifying
    @Query("""
            UPDATE DomainEventEntity e SET e.publishedAt = :publishedAt
            WHERE e.sequence IN :sequences AND e.publishedAt IS NULL
            """)
    int markPublished(@Param("sequences") Collection<Long> sequences, @Param("publishedAt") Instant publishedAt);

    /** Runs in its own transaction when called outside one (the scheduled retention run calls it that way). */
    @Transactional
    @Modifying
    @Query("DELETE FROM DomainEventEntity e WHERE e.recordedAt < :cutoff")
    int deleteRecordedBefore(@Param("cutoff") Instant cutoff);
}
