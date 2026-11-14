-- Stock reservations with a time limit (backend 3.21.0). Stock is still deducted when the order is
-- created; reservation_expires_at is when that hold ends. ReservationExpiryJob cancels PENDING orders
-- whose reservation has run out. Existing rows stay null and never expire.
-- Guarded like V19-V22: a schema baselined at V1 (legacy databases) may not have orders.
DO $migration$
BEGIN
    IF to_regclass('orders') IS NOT NULL THEN
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS reservation_expires_at timestamptz;
        -- Only PENDING orders with a reservation are candidates, so the job reads a small partial index.
        CREATE INDEX IF NOT EXISTS idx_orders_pending_reservation_expiry
            ON orders(reservation_expires_at, id)
            WHERE status = 'PENDING' AND reservation_expires_at IS NOT NULL;
    END IF;
END
$migration$;
