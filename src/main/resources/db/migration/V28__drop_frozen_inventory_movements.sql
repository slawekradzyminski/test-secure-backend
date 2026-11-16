-- warehouse-service owns the stock ledger since backend 3.22.0 (docs/ARCHITECTURE_DECISIONS.md ADR 6).
--
-- inventory_movements was frozen in V25: nothing wrote to it, and only the one-time ledger export
-- (GET /internal/v1/inventory/export) read it so warehouse-service could import the history. Every
-- deployment has imported it since, so the table goes. The export keeps serving product ids and
-- projected quantities, which is all a warehouse-service with an empty ledger needs to start.
--
-- A database still on 3.21 or older that skips 3.22 and 3.23 loses its movement history here;
-- quantities survive in products.stock_quantity. IF EXISTS keeps V1-baselined schemas working.
DROP TABLE IF EXISTS inventory_movements;
