package com.awesome.testing.repository;

import com.awesome.testing.entity.TrafficSessionOwnerEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface TrafficSessionOwnerRepository extends JpaRepository<TrafficSessionOwnerEntity, String> {

    /** Forgets bindings of sessions whose traffic has expired, so the table follows the traffic retention. */
    @Transactional
    @Modifying
    @Query("""
            delete from TrafficSessionOwnerEntity owner where owner.boundAt < :cutoff and not exists (
                select 1 from TrafficLogEntity log where log.clientSessionId = owner.clientSessionId)""")
    int deleteUnusedBoundBefore(@Param("cutoff") Instant cutoff);
}
