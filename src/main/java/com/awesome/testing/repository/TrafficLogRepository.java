package com.awesome.testing.repository;

import com.awesome.testing.entity.TrafficLogEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface TrafficLogRepository
        extends JpaRepository<TrafficLogEntity, Long>, JpaSpecificationExecutor<TrafficLogEntity> {

    Optional<TrafficLogEntity> findByCorrelationId(String correlationId);

    Optional<TrafficLogEntity> findByCorrelationIdAndClientSessionId(String correlationId, String clientSessionId);

    @Transactional
    @Modifying
    @Query("delete from TrafficLogEntity t where t.timestamp < :cutoff")
    int deleteByTimestampBefore(@Param("cutoff") Instant cutoff);
}
