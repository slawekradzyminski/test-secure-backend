package com.awesome.testing.entity.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Serialises checkout and warehouse-service's reaper on one stock reservation (table
 * {@code stock_reservation_claims}, V26). Both insert the reservation id; the primary key makes the
 * second writer wait for the first and then decides who wins:
 *
 * <ul>
 *   <li>checkout inserts and deletes its row inside the order transaction, so a reaper lookup that
 *       arrives meanwhile waits for the order to commit and then finds it;</li>
 *   <li>a reaper lookup that finds no order keeps its row (a tombstone), so a checkout that is still
 *       saving that order fails instead of storing an order whose stock was already given back.</li>
 * </ul>
 *
 * Writes go through {@link com.awesome.testing.repository.stock.StockReservationClaimRepository}'s
 * native inserts; this mapping only gives H2 tests the table and lets {@code ddl-auto=validate} check it.
 */
@Entity
@Table(name = "stock_reservation_claims")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationClaimEntity {

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
