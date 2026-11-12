-- A product without an image is stored as an empty string (catalog X-01). Rows written with a null
-- image URL (REST or GraphQL create without imageUrl, CSV import with an empty cell) crash-looped the
-- seeding profiles on the next start; the application now normalises null on every write, and this
-- migration converts existing rows and adds the database guarantee. Guarded like V15: a schema
-- baselined at V1 (legacy databases) may not have the products table.
DO $migration$
BEGIN
    IF to_regclass('products') IS NOT NULL THEN
        UPDATE products SET image_url = '' WHERE image_url IS NULL;
        ALTER TABLE products ALTER COLUMN image_url SET DEFAULT '';
        ALTER TABLE products ALTER COLUMN image_url SET NOT NULL;
    END IF;
END
$migration$;
