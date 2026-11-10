-- VAT rates and display currencies.
--
-- Shop prices are VAT-inclusive (gross), so no existing total changes. Checkout
-- resolves the shipping country to an ISO 3166-1 alpha-2 code and, when a rate
-- exists for it, stores the VAT contained in the order total on the order:
-- tax_amount = total_amount * rate / (100 + rate), net_amount = total_amount - tax_amount.
-- The four order columns stay NULL for unknown countries and for orders created
-- before this migration.
--
-- vat_rates.rate is the standard rate in percent. A rate of 0 means VAT is not
-- applicable (for example US and CA, which use sales taxes the shop does not model).
--
-- currency_rates.rate_to_usd is the value of one unit of the currency in USD
-- (USD itself is the base currency and is always 1). The rates are fixed demo
-- values, not market data.
CREATE TABLE vat_rates (
    country_code char(2) PRIMARY KEY,
    country_name varchar(64) NOT NULL,
    rate numeric(5,2) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT ck_vat_rates_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_vat_rates_rate CHECK (rate >= 0 AND rate <= 50)
);

INSERT INTO vat_rates (country_code, country_name, rate, updated_at) VALUES
    ('AT', 'Austria', 20.00, CURRENT_TIMESTAMP),
    ('BE', 'Belgium', 21.00, CURRENT_TIMESTAMP),
    ('BG', 'Bulgaria', 20.00, CURRENT_TIMESTAMP),
    ('HR', 'Croatia', 25.00, CURRENT_TIMESTAMP),
    ('CY', 'Cyprus', 19.00, CURRENT_TIMESTAMP),
    ('CZ', 'Czechia', 21.00, CURRENT_TIMESTAMP),
    ('DK', 'Denmark', 25.00, CURRENT_TIMESTAMP),
    ('EE', 'Estonia', 24.00, CURRENT_TIMESTAMP),
    ('FI', 'Finland', 25.50, CURRENT_TIMESTAMP),
    ('FR', 'France', 20.00, CURRENT_TIMESTAMP),
    ('DE', 'Germany', 19.00, CURRENT_TIMESTAMP),
    ('GR', 'Greece', 24.00, CURRENT_TIMESTAMP),
    ('HU', 'Hungary', 27.00, CURRENT_TIMESTAMP),
    ('IE', 'Ireland', 23.00, CURRENT_TIMESTAMP),
    ('IT', 'Italy', 22.00, CURRENT_TIMESTAMP),
    ('LV', 'Latvia', 21.00, CURRENT_TIMESTAMP),
    ('LT', 'Lithuania', 21.00, CURRENT_TIMESTAMP),
    ('LU', 'Luxembourg', 17.00, CURRENT_TIMESTAMP),
    ('MT', 'Malta', 18.00, CURRENT_TIMESTAMP),
    ('NL', 'Netherlands', 21.00, CURRENT_TIMESTAMP),
    ('PL', 'Poland', 23.00, CURRENT_TIMESTAMP),
    ('PT', 'Portugal', 23.00, CURRENT_TIMESTAMP),
    ('RO', 'Romania', 21.00, CURRENT_TIMESTAMP),
    ('SK', 'Slovakia', 23.00, CURRENT_TIMESTAMP),
    ('SI', 'Slovenia', 22.00, CURRENT_TIMESTAMP),
    ('ES', 'Spain', 21.00, CURRENT_TIMESTAMP),
    ('SE', 'Sweden', 25.00, CURRENT_TIMESTAMP),
    ('GB', 'United Kingdom', 20.00, CURRENT_TIMESTAMP),
    ('CH', 'Switzerland', 8.10, CURRENT_TIMESTAMP),
    ('NO', 'Norway', 25.00, CURRENT_TIMESTAMP),
    ('US', 'United States', 0.00, CURRENT_TIMESTAMP),
    ('CA', 'Canada', 0.00, CURRENT_TIMESTAMP);

CREATE TABLE currency_rates (
    code char(3) PRIMARY KEY,
    name varchar(64) NOT NULL,
    symbol varchar(8) NOT NULL,
    rate_to_usd numeric(12,6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT ck_currency_rates_code CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_currency_rates_rate CHECK (rate_to_usd > 0)
);

INSERT INTO currency_rates (code, name, symbol, rate_to_usd, updated_at) VALUES
    ('USD', 'US Dollar', '$', 1.000000, CURRENT_TIMESTAMP),
    ('EUR', 'Euro', '€', 1.080000, CURRENT_TIMESTAMP),
    ('PLN', 'Polish Zloty', 'zł', 0.250000, CURRENT_TIMESTAMP),
    ('GBP', 'British Pound', '£', 1.270000, CURRENT_TIMESTAMP),
    ('CHF', 'Swiss Franc', 'CHF', 1.130000, CURRENT_TIMESTAMP);

DO $migration$
BEGIN
    IF to_regclass('orders') IS NOT NULL THEN
        ALTER TABLE orders ADD COLUMN tax_country char(2);
        ALTER TABLE orders ADD COLUMN tax_rate numeric(5,2);
        ALTER TABLE orders ADD COLUMN tax_amount numeric(10,2);
        ALTER TABLE orders ADD COLUMN net_amount numeric(10,2);
        ALTER TABLE orders ADD CONSTRAINT ck_orders_tax CHECK (
            (tax_country IS NULL AND tax_rate IS NULL AND tax_amount IS NULL AND net_amount IS NULL)
            OR (tax_country IS NOT NULL AND tax_rate IS NOT NULL AND tax_amount IS NOT NULL AND net_amount IS NOT NULL
                AND tax_amount >= 0 AND net_amount >= 0));
    END IF;

    -- VAT contained in a refund, at the order's rate; NULL when the order carries no VAT.
    IF to_regclass('order_refunds') IS NOT NULL THEN
        ALTER TABLE order_refunds ADD COLUMN tax_amount numeric(10,2);
    END IF;
END
$migration$;
