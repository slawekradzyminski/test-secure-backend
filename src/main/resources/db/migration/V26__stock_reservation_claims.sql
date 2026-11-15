-- Checkout versus warehouse-service's reservation reaper (backend 3.22.2).
--
-- The reaper asks GET /internal/v1/orders/by-reservation/{id} before it releases a reservation that
-- was never confirmed. If the backend was still saving that order (its transaction not yet committed),
-- the lookup found nothing, the reaper released the stock, and the order was stored anyway without
-- stock. Both sides now insert the reservation id here first; the primary key makes the later writer
-- wait for the earlier one:
--   * checkout inserts and deletes its row inside the order transaction, so a lookup that arrives
--     meanwhile waits for the commit and then sees the order;
--   * a lookup that finds no order keeps its row as a tombstone, so a checkout still saving that
--     order fails (409) and the customer simply checks out again.
-- Only tombstones stay in the table: one row per reaped reservation.
CREATE TABLE IF NOT EXISTS stock_reservation_claims (
    reservation_id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL
);
