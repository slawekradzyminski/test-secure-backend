-- Orderings of GET /api/v1/products/search: name_asc sorts by lower(name) then id,
-- price_asc/price_desc and the minPrice/maxPrice range use price then id, and newest
-- sorts by created_at then id, both descending. Substring text matching cannot use a
-- B-tree index; ratings use idx_product_reviews_product_status from V7.
DO $migration$
BEGIN
    IF to_regclass('products') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_products_lower_name_id ON products(lower(name), id);
        CREATE INDEX IF NOT EXISTS idx_products_price_id ON products(price, id);
        CREATE INDEX IF NOT EXISTS idx_products_created_at_id ON products(created_at DESC, id DESC);
    END IF;
END
$migration$;
