-- warehouse-service owns the stock ledger (backend 3.22.0, docs/ARCHITECTURE_DECISIONS.md ADR 6).
--
-- products.stock_ledger_version: the warehouse ledger version the stock_quantity projection was
-- last set from. A stock level (checkout reply, adjustment reply, POST /internal/v1/inventory/levels)
-- is applied only when its ledgerVersion is greater. 0 means "never set by warehouse-service".
--
-- orders.stock_reservation_id: the warehouse reservation a checkout created. Internal only, never
-- serialized; warehouse-service looks it up (GET /internal/v1/orders/by-reservation/{id}) before it
-- releases an unconfirmed reservation.
--
-- stock_command_outbox: stock commands that must reach warehouse-service after the product change
-- commits (INITIAL_STOCK for product create, CSV import and seeding; DELETE for product delete).
-- StockCommandDispatcher sends a row right after commit and retries due rows every PT5S.
-- No foreign key to products: a DELETE row outlives its product.
--
-- inventory_movements is frozen from this release: read only for the one-time export
-- (GET /internal/v1/inventory/export) and dropped in a later release.
--
-- Guarded like V19-V24: a schema baselined at V1 (legacy databases) may not have these tables.
DO $migration$
BEGIN
    IF to_regclass('products') IS NOT NULL THEN
        ALTER TABLE products ADD COLUMN IF NOT EXISTS stock_ledger_version bigint NOT NULL DEFAULT 0;
    END IF;
    IF to_regclass('orders') IS NOT NULL THEN
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_reservation_id uuid;
        CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_stock_reservation
            ON orders(stock_reservation_id) WHERE stock_reservation_id IS NOT NULL;
    END IF;
END
$migration$;

CREATE TABLE IF NOT EXISTS stock_command_outbox (
    id bigserial PRIMARY KEY,
    command varchar(16) NOT NULL CHECK (command IN ('INITIAL_STOCK', 'DELETE')),
    product_id bigint NOT NULL,
    initial_quantity integer CHECK (initial_quantity IS NULL OR initial_quantity >= 0),
    actor varchar(255),
    request_id uuid NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'DONE')),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    last_error varchar(500),
    correlation_request_id varchar(128),
    traceparent varchar(55),
    created_at timestamptz NOT NULL,
    processed_at timestamptz,
    CONSTRAINT uk_stock_command_outbox_request UNIQUE (request_id)
);
CREATE INDEX IF NOT EXISTS idx_stock_command_outbox_due
    ON stock_command_outbox(next_attempt_at, id) WHERE status = 'PENDING';
