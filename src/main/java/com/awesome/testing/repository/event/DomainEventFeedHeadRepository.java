package com.awesome.testing.repository.event;

import com.awesome.testing.entity.event.DomainEventFeedHeadEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DomainEventFeedHeadRepository extends JpaRepository<DomainEventFeedHeadEntity, Integer> {

    /** {@code SELECT ... FOR UPDATE}: one feed position assigner at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM DomainEventFeedHeadEntity h WHERE h.id = :id")
    Optional<DomainEventFeedHeadEntity> findForUpdate(@Param("id") Integer id);
}
