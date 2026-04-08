package com.awesome.testing.repository;

import com.awesome.testing.entity.TrafficLogEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

public interface TrafficLogRepository extends JpaRepository<TrafficLogEntity, Long>, JpaSpecificationExecutor<TrafficLogEntity> {

    Optional<TrafficLogEntity> findByCorrelationId(String correlationId);

    @Transactional
    long deleteByTimestampBefore(Instant cutoff);
}
