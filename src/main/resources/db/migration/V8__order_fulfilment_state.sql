-- Warehouse progress mirrored from warehouse-service, which owns shipments,
-- returns, suppliers and purchase orders in its own schema. Existing orders
-- were never released to the warehouse, so they start as NONE.
DO $migration$
BEGIN
    IF to_regclass('orders') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE orders ADD COLUMN fulfilment_state varchar(16) NOT NULL DEFAULT 'NONE';
    ALTER TABLE orders ADD CONSTRAINT ck_orders_fulfilment_state CHECK (fulfilment_state IN (
        'NONE', 'AWAITING_PICK', 'PICKED', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED'));
    CREATE INDEX idx_orders_status_fulfilment_created ON orders(status, fulfilment_state, created_at, id);
END
$migration$;
