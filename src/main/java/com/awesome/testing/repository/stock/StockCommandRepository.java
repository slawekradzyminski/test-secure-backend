package com.awesome.testing.repository.stock;

import com.awesome.testing.entity.stock.StockCommandEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockCommandRepository extends JpaRepository<StockCommandEntity, Long> {

    /**
     * Pending commands due at {@code now}, oldest first, locked with {@code FOR UPDATE SKIP LOCKED}
     * (lock timeout -2 is Hibernate's SKIP_LOCKED) so concurrent dispatchers claim different rows.
     * A command waits while an older command of the same product is still pending (in flight or
     * waiting for a retry), so a product's commands reach warehouse-service in order: a DELETE is
     * never sent before, or overtaken by, the INITIAL_STOCK of the same product.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select c from StockCommandEntity c where c.status = :status and c.nextAttemptAt <= :now"
            + " and not exists (select o.id from StockCommandEntity o where o.productId = c.productId"
            + " and o.status = :status and o.id < c.id)"
            + " order by c.id")
    List<StockCommandEntity> findDueForUpdate(
            @Param("status") StockCommandEntity.Status status, @Param("now") Instant now, Pageable pageable);

    long countByStatus(StockCommandEntity.Status status);

    /** The row when it is still {@code status}, locked with {@code FOR UPDATE SKIP LOCKED}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select c from StockCommandEntity c where c.id = :id and c.status = :status")
    java.util.Optional<StockCommandEntity> findPendingForUpdate(@Param("id") long id,
                                                                @Param("status") StockCommandEntity.Status status);

    java.util.Optional<StockCommandEntity> findFirstByProductIdAndCommandAndStatusOrderByIdAsc(
            Long productId, StockCommandEntity.Command command, StockCommandEntity.Status status);

    /**
     * Completes the never-sent ({@code attempts = 0}) pending commands of a product with
     * {@code note}; used when a DELETE supersedes its INITIAL_STOCK. A command already claimed has
     * attempts above 0 and is left to the dispatcher, which completes it without a call once the
     * product is gone and only then sends the DELETE.
     */
    @Modifying
    @Query("update StockCommandEntity c set c.status = :done, c.processedAt = :now, c.lastError = :note"
            + " where c.productId = :productId and c.command = :command and c.status = :pending and c.attempts = 0")
    int completeUnsent(@Param("productId") long productId, @Param("command") StockCommandEntity.Command command,
                       @Param("pending") StockCommandEntity.Status pending, @Param("done") StockCommandEntity.Status done,
                       @Param("now") Instant now, @Param("note") String note);
}
