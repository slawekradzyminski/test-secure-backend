package com.awesome.testing.repository.stock;

import com.awesome.testing.entity.stock.StockReservationClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Claims on stock reservations; see {@link StockReservationClaimEntity}. */
public interface StockReservationClaimRepository extends JpaRepository<StockReservationClaimEntity, UUID> {

    /**
     * Inserts the claim. Waits while another open transaction holds the same reservation id and throws
     * {@link org.springframework.dao.DataIntegrityViolationException} when a committed row already has it.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into stock_reservation_claims (reservation_id, created_at) values (:reservationId, :now)",
            nativeQuery = true)
    void insert(@Param("reservationId") UUID reservationId, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query(value = "delete from stock_reservation_claims where reservation_id = :reservationId", nativeQuery = true)
    int deleteClaim(@Param("reservationId") UUID reservationId);
}
