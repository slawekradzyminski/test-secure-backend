-- Preserve support for minimal legacy schemas used by token-only upgrades.
DO $migration$
BEGIN
    IF to_regclass('orders') IS NOT NULL THEN
        CREATE INDEX idx_orders_username_id ON orders(username, id);
        CREATE INDEX idx_orders_username_status_id ON orders(username, status, id);
        CREATE INDEX idx_orders_status_id ON orders(status, id);
    END IF;
    IF to_regclass('order_items') IS NOT NULL THEN
        CREATE INDEX idx_order_items_order ON order_items(order_id);
        CREATE INDEX idx_order_items_product ON order_items(product_id);
    END IF;
    IF to_regclass('cart_items') IS NOT NULL THEN
        CREATE INDEX idx_cart_items_product ON cart_items(product_id);
    END IF;
    IF to_regclass('products') IS NOT NULL THEN
        CREATE INDEX idx_products_category_id ON products(lower(category), id);
    END IF;
    IF to_regclass('traffic_log') IS NOT NULL THEN
        CREATE INDEX idx_traffic_log_session_time ON traffic_log(client_session_id, "timestamp" DESC, id DESC);
        DROP INDEX IF EXISTS idx_traffic_log_client_session_id;
    END IF;
END
$migration$;
