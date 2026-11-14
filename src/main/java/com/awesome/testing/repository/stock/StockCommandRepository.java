package com.awesome.testing.repository.stock;

import com.awesome.testing.entity.stock.StockCommandEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockCommandRepository extends JpaRepository<StockCommandEntity, Long> {

    /**
     * Pending commands due at {@code now}, oldest first, locked with {@code FOR UPDATE SKIP LOCKED}
     * (lock timeout -2 is Hibernate's SKIP_LOCKED) so concurrent dispatchers claim different rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select c from StockCommandEntity c where c.status = :status and c.nextAttemptAt <= :now order by c.id")
    List<StockCommandEntity> findDueForUpdate(
            @Param("status") StockCommandEntity.Status status, @Param("now") Instant now, Pageable pageable);

    long countByStatus(StockCommandEntity.Status status);
}
