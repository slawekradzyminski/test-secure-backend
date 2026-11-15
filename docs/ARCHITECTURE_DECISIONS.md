# Architecture decisions (backend)

The system-wide decision records live in awesome-localstack's
`docs/ARCHITECTURE_DECISIONS.md` (ADR 1 to 5: the pull event feed, the fulfilment read
model, per-service outboxes, database per service, notifications moving out; ADR 7: the
broker transport next to the feed, implemented here by the outbox relay `EventRelay`, V27 and
`events.transport`). This file
records the decisions that change how the backend itself works, with the same numbering.

| ADR | Decision | Status | Since |
| --- | --- | --- | --- |
| [6](#adr-6-warehouse-service-owns-the-stock-ledger) | warehouse-service owns the stock ledger; the backend calls it for stock (reverses ADR 1 for stock only) | Accepted | backend 3.22.0, warehouse-service 1.6.0 |

## ADR 6: warehouse-service owns the stock ledger

**Status:** accepted, November 2026 (backend 3.22.0, warehouse-service 1.6.0). Partly
supersedes ADR 1 ("the backend calls no service"): for stock, and only for stock, the
backend calls warehouse-service synchronously. The contract is awesome-localstack's
`docs/INVENTORY_OWNERSHIP_CONTRACT.md`.

### Context

Up to 3.21.x the backend owned stock (`products.stock_quantity` and
`inventory_movements`), while warehouse-service owned everything physical: receipts,
returns, shipments, locations. Every receipt and restock was a synchronous call from
warehouse-service to `POST /internal/v1/inventory/receipts`, and the warehouse could not
answer "how much do we have" without asking the backend. Stock is a warehouse concern; the
split put the ledger in the wrong service and made the backend's checkout transaction the
only place where the stock rules lived.

### Decision

warehouse-service owns the ledger: stock items, movements and per-checkout reservations,
with a per-product `ledgerVersion`. The backend keeps:

- `products.stock_quantity` as a **projection** with `products.stock_ledger_version`,
  updated from command replies and from warehouse-service's level push
  (`POST /internal/v1/inventory/levels`) only when the version is newer, so reads, the
  cart's availability check, product lists, the course's inventory endpoints and GraphQL
  and gRPC reads stay local and keep working while warehouse-service is down;
- the commands as **synchronous calls** to warehouse-service (`StockLedgerClient`):
  reserve and confirm at checkout, release as compensation, admin adjustments. Without the
  ledger the backend cannot promise stock, so these answer `503 Inventory service
  unavailable` instead of guessing;
- initial stock and deletes in a **transactional outbox** (`stock_command_outbox`,
  ADR 3: the backend's own small implementation), because they follow a product change
  that has already committed and must not fail the public request;
- the restore after a cancellation as an **internal feed event**:
  `stock.restore-requested` (`{orderId, actor, lines}`), recorded in the cancel's
  transaction and consumed by warehouse-service from the existing feed (ADR 1) once per
  order. It is never offered to webhooks and `order.cancelled` stays unchanged, so no
  staff username reaches customer webhooks;
- the initial stock of a product created by a request is sent **synchronously after
  commit, before the response**, so a product can be ordered as soon as it exists; the
  outbox only retries a failed send, and a reservation that hits a product warehouse-service
  does not know yet sends its pending initial stock and retries once.

Checkout never holds a database connection during an HTTP call (the production pool has
three connections): a read transaction validates, the reservation runs with no
transaction, a write transaction stores the order with the reservation id and applies the
projection, and the confirm runs with no transaction. A failed write releases the
reservation; a failed confirm is left to warehouse-service's reaper, which asks
`GET /internal/v1/orders/by-reservation/{id}` before it releases anything. The client
enforces the rule: it refuses to run inside a transaction.

That lookup and the order transaction meet on one row (`stock_reservation_claims`, V26,
backend 3.22.2). The order transaction inserts and deletes the reservation id first; a
lookup inserts it as a tombstone before it reads `orders`. The primary key makes the
later writer wait for the earlier one, so a lookup that arrives while the order is being
saved waits and finds it, and an order that is still being saved after a lookup found
nothing fails with 409 instead of being stored without stock. No timeout has to be
shorter than the reaper's confirm timeout for this to hold.

Alternatives considered:

- **Keep the ledger in the backend** and let warehouse-service keep calling it: rejected,
  the reason for the move.
- **Asynchronous reservations** (order PENDING until a stock event confirms it): rejected
  for the course contract, which expects the stock to be deducted when `POST
  /api/v1/orders` answers 201 and the 409 `Insufficient stock for product <id>` on that
  same call.

### Consequences

- The dependency graph has its first backend → warehouse-service edge. The backend still
  starts, serves reads and carts without warehouse-service; checkout, admin adjustments,
  stock changes on product update and CSV import, and movement history answer 503 while
  it is down (the fault lab's `warehouse-down` scenario).
- After a cancel, stock and movements change **eventually**: feed settle delay (3 s) +
  warehouse-service's poll interval (5 s) + the level push. Clients poll; teaching
  scenarios that read stock right after a cancel now wait.
- `inventory_movements` is frozen (read only by `GET /internal/v1/inventory/export`, the
  one-time import) and dropped in a later release, together with
  `POST /internal/v1/inventory/receipts`, which answers 410 for one release.
- `BACKEND_SERVICE_TOKEN` is a new secret shared by the backend (sends it) and
  warehouse-service (expects it), per site.
- Public REST, GraphQL and gRPC bodies and the 409 messages are unchanged; 503 is new.
