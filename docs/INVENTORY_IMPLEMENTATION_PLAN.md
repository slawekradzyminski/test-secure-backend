# Inventory implementation plan

## Goal

Turn the existing `ProductEntity.stockQuantity` catalog field into enforced,
transactional inventory and add an administrator API for browsing, adjusting,
and reviewing inventory. The feature is intentionally designed as a rich test
surface for authorization, validation, transactions, concurrency, migrations,
idempotency, and state transitions.

Inventory remains an internal module of the existing backend. This increment
does not add another service, broker destination, or Compose dependency.

## Fixed domain decisions

- `products.stock_quantity` is the canonical available-to-sell quantity.
- A cart is not a reservation. Cart writes may reject a quantity that already
  exceeds current availability, but checkout always performs the authoritative
  check because availability can change after an item enters a cart.
- Checkout is all-or-nothing. Every line is deducted or no stock, order, or cart
  state changes are committed.
- Product rows are locked in ascending product ID order before validation and
  mutation. This prevents overselling and reduces multi-row deadlock risk.
- Cart rows are write-locked during checkout so two concurrent submissions of
  the same cart cannot create two orders.
- Product rows carry an optimistic version in addition to explicit inventory
  locks so a concurrent catalog update cannot silently overwrite inventory.
- Stock may never become negative. Enforce this in both domain logic and the
  database.
- Cancelling a `PENDING` or `PAID` order restores its deducted stock exactly
  once. Both client cancellation and admin `status=CANCELLED` use the same
  transition.
- Existing persisted and seeded orders did not deduct stock. Mark them
  `LEGACY_UNTRACKED`; cancelling them must not add stock.
- New orders use internal inventory states:
  - `DEDUCTED`: checkout reduced available stock.
  - `RESTORED`: cancellation compensated the deduction.
  - `LEGACY_UNTRACKED`: the order predates inventory enforcement.
- Existing successful product, cart, and order response shapes remain stable.
- Insufficient availability and conflicting inventory writes return HTTP 409.
- Product creation remains the way to create a sellable catalog item. The
  inventory API replenishes or corrects stock for an existing product; it does
  not create a second inventory identity.

## Administrator API

All new routes require `ROLE_ADMIN` and keep the existing bearer-token security
contract.

### List inventory

`GET /api/v1/admin/inventory`

Query parameters:

- `page` (default `0`)
- `size` (default `20`, bounded to `1..100`)
- `search` (optional case-insensitive product-name match)
- `category` (optional case-insensitive exact match)
- `status` (optional: `IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK`)
- `lowStockThreshold` (default `5`, minimum `1`)

Return `PageDto<InventoryItemDto>` with stable product-ID ordering. Each item
contains `productId`, `name`, `category`, `availableQuantity`, `stockStatus`,
and `lastChangedAt`.

Status classification:

- `OUT_OF_STOCK`: available quantity is zero.
- `LOW_STOCK`: available quantity is between one and the threshold, inclusive.
- `IN_STOCK`: available quantity is greater than the threshold.

### Read one inventory item

`GET /api/v1/admin/inventory/{productId}` returns `InventoryItemDto` or 404.

### Adjust inventory

`POST /api/v1/admin/inventory/{productId}/adjustments`

Request:

```json
{
  "delta": 25,
  "reason": "Supplier delivery",
  "requestId": "0e6863f5-b0f6-4d27-b484-b23af48ea661"
}
```

- Positive `delta` adds available stock.
- Negative `delta` removes available stock.
- Zero is invalid.
- `reason` is required and bounded to 500 characters.
- `requestId` is required and idempotent per product.
- Replaying the same product/request/payload returns the original movement
  without changing stock.
- Reusing a request ID for a different payload on the same product returns 409.
- An adjustment that would make stock negative returns 409 and records nothing.

The response is `InventoryMovementDto` and includes the resulting quantity.

### List movement history

`GET /api/v1/admin/inventory/{productId}/movements`

Return a newest-first `PageDto<InventoryMovementDto>`. Each movement contains
`id`, `productId`, optional `orderId`, `type`, `delta`, `quantityAfter`, `actor`,
`reason`, optional `requestId`, and `createdAt`.

Movement types:

- `INITIAL_STOCK`
- `ADMIN_ADJUSTMENT`
- `ORDER_DEDUCTED`
- `ORDER_RESTORED`

## Persistence changes

Add a Flyway V4 migration that:

1. Adds `products.version bigint NOT NULL DEFAULT 0`.
2. Adds `CHECK (stock_quantity >= 0)`.
3. Adds `orders.inventory_state`, defaults existing rows to
   `LEGACY_UNTRACKED`, and constrains allowed values.
4. Creates `inventory_movements` with:
   - generated bigint primary key;
   - product foreign key with delete cascade to preserve the existing product
     deletion contract;
   - optional order ID with delete-set-null semantics;
   - movement type, signed non-zero delta, non-negative resulting quantity;
   - actor, reason, optional UUID request ID, and creation timestamp;
   - unique `(product_id, request_id)` constraint;
   - product/time and order indexes.

The JPA mapping must generate an equivalent shape for H2 `create-drop` tests.

## Application changes

### Inventory service

Create one service that is the only application writer of stock quantities.
It owns:

- locked inventory reads;
- cart availability checks;
- checkout deduction and `ORDER_DEDUCTED` movements;
- cancellation restoration and `ORDER_RESTORED` movements;
- initial-stock movement creation;
- absolute product-update compatibility;
- idempotent admin adjustments;
- inventory listing/status classification;
- movement-history reads.

Movement persistence and quantity mutation always share one transaction.

### Cart integration

- Preserve POST-accumulates, PUT-replaces, and zero-removes semantics.
- Reject a resulting non-zero cart quantity above current availability with 409.
- This is advisory validation only; checkout still locks and rechecks stock.

### Checkout integration

1. Lock the authenticated user's cart rows.
2. Reject an empty cart with the existing HTTP 400 contract.
3. Build and persist the `PENDING`, `DEDUCTED` order inside the transaction.
4. Aggregate quantities and lock all referenced products in ascending ID order.
5. Validate every product before mutating any quantity.
6. Deduct stock and write one movement per order line.
7. Delete the cart only after successful deduction.
8. Return the existing order DTO and HTTP 201 response.

Any failure rolls back the order insert, all movements, stock changes, and cart
deletion.

### Cancellation integration

- Lock the order row before checking authorization, status, or inventory state.
- Preserve owner/admin authorization and existing cancellable statuses.
- Restore stock only for `DEDUCTED`; then mark the order `RESTORED`.
- `LEGACY_UNTRACKED` orders change status without changing inventory.
- `RESTORED` or already-cancelled orders cannot restore again.
- Admin `status=CANCELLED` delegates to the same transition.

### Existing product writes

- Creating a product records `INITIAL_STOCK` in the same transaction.
- Keep `ProductUpdateDto.stockQuantity` for compatibility.
- Route an absolute stock update through inventory locking and record the delta
  as `ADMIN_ADJUSTMENT`; a no-op absolute value records nothing.
- Preserve the existing unrelated `ProductService` and `ProductServiceTest`
  working-tree changes while implementing this integration.

## Test contract

### Unit and service tests

- exact availability succeeds and reaches zero;
- insufficient availability changes nothing;
- all product locks use stable ID ordering;
- multi-line validation happens before mutation;
- positive, negative, zero, and below-zero adjustments;
- idempotent adjustment replay and conflicting reuse;
- initial, deducted, restored, and admin movement mapping;
- cancellation restores once through both public paths;
- legacy cancellation never changes stock;
- cart POST accumulation and PUT replacement use the resulting quantity.

### HTTP tests

- admin can list, filter, read, adjust, and review inventory;
- client receives 403 and unauthenticated request receives 401;
- validation and 404/409 payloads are stable;
- successful checkout visibly decreases product stock;
- failed checkout retains the cart and creates no order;
- cancellation restores the visible quantity exactly once.

Serialize shared H2 ecommerce tests with a common JUnit resource lock so their
table-wide cleanup cannot race across classes.

### PostgreSQL integration tests

Add explicit Failsafe/Testcontainers coverage for:

- two users competing for the final unit: one succeeds, one receives a stock
  conflict, final stock is zero, and exactly one order exists;
- two submissions of one cart: at most one order is created;
- a multi-line checkout with one unavailable product: no quantity or movement
  is committed;
- the Flyway migration on a fresh schema and an upgrade schema with legacy
  orders.

## Verification order

1. Focused unit/service tests.
2. Focused inventory/cart/order HTTP tests.
3. `./mvnw -Pfast-verify verify`.
4. `./mvnw verify`.
5. `./mvnw -Pintegration-tests verify`.
6. Add inventory production/tests to the PITest target lists, then run
   `./mvnw -Pmutation-testing test-compile pitest:mutationCoverage`.
7. Run one to three frozen semantic mutants in a disposable copy for concurrent
   oversell, partial checkout commit, and double restoration. Report PITest and
   semantic results separately.
8. Run the latest `l12` external course API contract.

## Deployment and compatibility

- No new Compose service, environment variable, or gateway route is required.
- After a backend release, synchronize the immutable backend image across full,
  lightweight, server, and AI-testers services.
- Validate every changed Compose file with `docker compose config --quiet`.
- Preserve all existing course-covered endpoints and successful response shapes.

## Deferred work

- expiring reservations and payment/expiry races;
- multiple warehouses or locations;
- backorders and partial fulfillment;
- supplier purchase orders and forecasting;
- immutable compliance-grade audit retention after product deletion;
- event sourcing or a separate inventory service.

## Completion criteria

The increment is complete only when the admin inventory API works, every stock
write produces consistent movement history, checkout and cancellation enforce
the stated invariants under PostgreSQL concurrency, migrations work for legacy
data, normal and mutation verification pass, and the external course contract
remains compatible.
