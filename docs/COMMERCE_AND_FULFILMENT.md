# Commerce extensions and warehouse fulfilment

This document describes the training features added on top of the original
shop. Everything lives inside the existing backend: no additional container or
service is required, so the production memory budget is unchanged.

Compatibility rules followed by every feature:

- Existing request bodies, paths, status codes, and enum values are unchanged.
  `OrderStatus`, the GraphQL schema, and the gRPC contract keep their values.
- New response fields are additive. Clients that ignore unknown JSON fields are
  unaffected.
- New behaviour on existing endpoints is opt-in (`Idempotency-Key`,
  `If-Match`, `If-None-Match`).

## Roles

| Role | Purpose |
| --- | --- |
| `ROLE_CLIENT` | Shopper. Coupons, reviews, wishlist, returns, own shipment tracking. |
| `ROLE_WAREHOUSE` | Warehouse operator. Fulfilment queue and shipment state machine. |
| `ROLE_ADMIN` | Everything, including warehouse endpoints, coupons, moderation, returns, purchasing, CSV. |

Signup always creates `ROLE_CLIENT`. The demo seed adds `warehouse` / `warehouse`.

## Tier 1: shop features

### Coupons

Admin CRUD under `/api/v1/admin/coupons`; the shopper applies one coupon to the
cart with `PUT /api/v1/cart/coupon` and removes it with `DELETE /api/v1/cart/coupon`.

| Rule | Result |
| --- | --- |
| Code format `^[A-Z0-9-]{3,32}$`; input is trimmed and upper-cased | 400 on violation |
| Unknown code | 404 `Coupon not found` |
| `active = false` | 400 `Coupon is not active` |
| now before `validFrom` | 400 `Coupon is not valid yet` |
| now after `validUntil` | 400 `Coupon has expired` |
| cart subtotal below `minOrderAmount` | 400 `Order subtotal must be at least <amount>` |
| global `maxRedemptions` reached | 409 `Coupon usage limit reached` |
| user's `perUserLimit` reached | 409 `Coupon already used the maximum number of times` |
| empty cart | 400 `Cart is empty` |

- `PERCENTAGE`: value 1–100, discount = subtotal × value / 100 rounded half-up to cents.
- `FIXED_AMOUNT`: value > 0, discount = min(value, subtotal).
- Only one coupon per cart. Applying another replaces the first.
- Checkout re-validates the coupon. If it is no longer valid, checkout fails
  with 409 `Applied coupon is no longer valid: <reason>` and the cart is kept.
- The order stores `subtotalAmount`, `discountAmount`, `couponCode`;
  `totalAmount = subtotalAmount - discountAmount`.
- Cancelling an order releases its redemption, so the usage is returned.
- A coupon with redemptions cannot be deleted (409); deactivate it instead.

### Reviews and ratings

- `POST /api/v1/products/{id}/reviews`: rating 1–5, title 3–120, body 10–2000.
  Allowed only when the user has a `DELIVERED` order containing the product
  (otherwise 403). One review per user and product (409). New reviews are `PENDING`.
- `PUT|DELETE /api/v1/products/{id}/reviews/mine`: editing returns the review to `PENDING`.
- `GET /api/v1/products/{id}/reviews/mine`: eligibility plus the caller's review.
- `GET /api/v1/products/{id}/reviews`: approved reviews only, newest first.
- `GET /api/v1/products/{id}/reviews/summary`: average (one decimal, half-up),
  count, and the 1–5 distribution over approved reviews only.
- Moderation: `GET /api/v1/admin/reviews?status=`,
  `PUT /api/v1/admin/reviews/{id}/moderation` with `APPROVED` or `REJECTED`;
  rejection requires a note (400).

### Wishlist and back-in-stock notifications

- `GET /api/v1/wishlist`, `PUT /api/v1/wishlist/{productId}` (201 created,
  200 updated), `DELETE /api/v1/wishlist/{productId}` (204, 404 when absent).
- At most 50 products per user (409).
- When stock moves from 0 to a positive quantity (admin adjustment, product
  edit, cancellation restore, return restock, purchase receipt), every
  wishlist entry with `notifyWhenAvailable = true` receives one
  `BACK_IN_STOCK` email after the transaction commits. Entries are not
  notified again until the product sells out and is replenished again.

### Product CSV export and import

- `GET /api/v1/admin/products/export` returns RFC 4180 `text/csv` with header
  `id,name,description,price,stockQuantity,category,imageUrl`. Cells that start
  with `=`, `+`, `-`, `@`, tab, or carriage return are prefixed with `'` to
  prevent spreadsheet formula injection.
- `POST /api/v1/admin/products/import` (multipart field `file`, optional
  `dryRun=true`). At most 1 MB and 1000 data rows. A blank `id` creates a
  product; an existing `id` updates it; an unknown `id` is a row error.
- Validation is all-or-nothing. Any row error returns 400 with the full report
  and persists nothing. A valid dry run returns 200 and persists nothing.
- Stock changes are recorded as inventory movements (`INITIAL_STOCK` or
  `ADMIN_ADJUSTMENT` with reason `CSV import`).

### Idempotent checkout

`POST /api/v1/orders` accepts an optional `Idempotency-Key` header
(`^[A-Za-z0-9_-]{8,64}$`, otherwise 400). Within 24 hours, repeating the key
with the same address returns the original order with 201 and
`Idempotent-Replayed: true`; a different address returns 409. Keys are scoped
per user.

### Conditional product requests

- `GET /api/v1/products/{id}` returns `ETag: "<version>"` and honours
  `If-None-Match` with 304.
- `PUT /api/v1/products/{id}` honours `If-Match`; a stale version returns 412.
  Without `If-Match` the update behaves as before.

## Tier 2: warehouse fulfilment

### Shipments

Endpoints under `/api/v1/warehouse` require `ROLE_WAREHOUSE` or `ROLE_ADMIN`.

```
            create          pick           pack            ship              deliver
PAID order ───────> AWAITING_PICK ───> PICKED ───> PACKED ───────> SHIPPED ───────> DELIVERED
                        │                │
                        └──── cancel ────┴──> CANCELLED (order cancelled, stock restored)
```

- `GET /queue` lists `PAID` orders without a shipment, oldest first.
- `POST /shipments` with `orderId` creates the shipment (order must be `PAID`, 409
  otherwise or when one exists).
- `GET /shipments?status=`, `GET /shipments/{id}`, `GET /shipments/{id}/pick-list`.
- `POST /shipments/{id}/pick|pack|ship|deliver`. Any other transition is 409.
  `pack` requires `weightKg` 0.01–30.00; `ship` requires a carrier and a
  tracking number that matches the carrier:

| Carrier | Tracking number |
| --- | --- |
| `INPOST` | 24 digits |
| `DPD` | 14 digits |
| `DHL` | 10 digits |
| `UPS` | `1Z` + 16 upper-case letters or digits |

- `ship` sets the order to `SHIPPED`; `deliver` sets it to `DELIVERED`. Both
  send an email to the customer.
- `POST /shipments/{id}/cancel` is allowed in `AWAITING_PICK` and `PICKED`; it
  cancels the order and restores stock.
- While a shipment is active, the order cannot be cancelled by the customer
  once it is `PICKED` or later (409), and the legacy admin status endpoint
  rejects manual status changes with 409. In `AWAITING_PICK`, cancelling the
  order also cancels the shipment.
- `GET /api/v1/orders/{id}/shipment` lets the order owner (or an admin) track it.

### Returns (RMA)

```
REQUESTED ──approve──> APPROVED ──receive──> RECEIVED ──refund──> REFUNDED
    └──reject──> REJECTED
```

- `POST /api/v1/orders/{id}/returns` (owner only): order must be `DELIVERED`,
  at most 30 days after delivery. Each item quantity must not exceed the
  ordered quantity minus quantities in other non-rejected returns.
  Reason is one of `DAMAGED`, `WRONG_ITEM`, `NOT_AS_DESCRIBED`, `NO_LONGER_NEEDED`.
- `GET /api/v1/returns`, `GET /api/v1/returns/{id}` for the owner.
- Admin: `GET /api/v1/admin/returns?status=`, `POST .../{id}/approve`,
  `POST .../{id}/reject` (note required), `POST .../{id}/receive` with a
  condition (`RESELLABLE` or `DAMAGED`) for every item, `POST .../{id}/refund`.
- Receiving restocks `RESELLABLE` items (`RETURN_RESTOCKED` movement).
- Refund = Σ(unit price × quantity) × (order total ÷ order subtotal), rounded
  half-up, so coupon discounts are returned proportionally.

### Suppliers and purchase orders

- `GET|POST /api/v1/admin/suppliers`.
- `/api/v1/admin/purchase-orders`: create (`DRAFT`), list, get, update lines
  (`DRAFT` only), `submit` (needs at least one line; sets
  `expectedAt = submittedAt + supplier lead time`), `cancel` (`DRAFT` or `SUBMITTED`),
  and `receipts`.
- Receipts carry a `requestId` and quantities per product. Receiving more than
  is outstanding is 409. A repeated `requestId` with the same lines replays the
  previous result; different lines with the same `requestId` are 409. Status becomes
  `PARTIALLY_RECEIVED` or `RECEIVED`. Stock rises with `PURCHASE_RECEIVED`
  movements.
- `GET /api/v1/admin/inventory/reorder-suggestions?lowStockThreshold=5`:
  products whose `stock + incoming` is at or below the threshold, with
  `suggestedQuantity = 4 × threshold − stock − incoming`.
