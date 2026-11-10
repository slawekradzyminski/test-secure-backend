# Commerce extensions and warehouse fulfilment

This document describes the training features added on top of the original
shop. The Tier 1 shop features live in this backend. Warehouse fulfilment
(Tier 2: shipments, returns, suppliers and purchase orders) lives in the
separate `warehouse-service`; see the last section.

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
| `ROLE_CLIENT` | Shopper. Coupons, reviews, wishlist (returns and shipment tracking in warehouse-service). |
| `ROLE_WAREHOUSE` | Warehouse operator. Used by warehouse-service for the fulfilment queue and shipments. |
| `ROLE_ADMIN` | Everything, including coupons, moderation and CSV (and the warehouse-service admin endpoints). |

Signup always creates `ROLE_CLIENT`. The demo seed adds `warehouse` / `warehouse`.
The backend issues the RS256 JWTs for all three roles; warehouse-service
verifies them with the public keys from `GET /internal/v1/jwks.json`.

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

### Customer emails

The backend renders the subject and body of every customer email; the JMS
consumer only delivers them, and the local outbox shows the same text. Each
email sent to a known user is recorded in `email_event` with its payload. A
send that fails (for example while Artemis is down) is retried by
`EmailRetryJob` every minute with exponential backoff (1, 2, 4, 8 minutes; five
attempts in total), and a send lost in a restart during the training delay is
retried after 20 minutes. `APP_EMAIL_RETRY_ENABLED` and
`APP_EMAIL_RETRY_INTERVAL` configure the job.

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

### VAT and display currencies

Prices are VAT-inclusive (gross), so no total changes. Orders show the VAT
their total contains; currencies are for display only, and prices and orders
stay in USD.

- Rates live in `vat_rates` (migration V13): the EU-27 standard rates plus
  GB 20, CH 8.1, NO 25, US 0 and CA 0. A rate of 0 means VAT is not applicable.
- Checkout resolves the free-text shipping `country` to an ISO 3166-1 alpha-2
  code (`CountryResolver`: alpha-2 and alpha-3 codes, English names and
  variants such as `UK`, `Great Britain`, `USA`, `Polska`, `Deutschland`;
  case, dots and extra whitespace are ignored; any other two letters are taken
  as a code). When a rate exists, `VatCheckoutContributor` stores it on the
  order after coupon pricing:
  `taxAmount = totalAmount - totalAmount / (1 + rate / 100)`, computed exactly
  and rounded half-up to cents; `netAmount = totalAmount - taxAmount`.
- `OrderDto` adds `taxCountry`, `taxRate`, `taxAmount` and `netAmount`, fixed
  at checkout, so POST, GET and list return the same values and a later rate
  change affects only new orders. Unknown countries (and orders created before
  V13) omit all four, so their JSON is unchanged. Order items and the cart JSON
  never change.
- A refund booked for an order with VAT records the VAT it contains at the
  order's rate in `order_refunds.tax_amount` (not exposed in responses).

| Method and path | Access | Behaviour |
| --- | --- | --- |
| `GET /api/v1/vat-rates` | authenticated | `[{countryCode, countryName, rate, updatedAt}]` by code |
| `GET /api/v1/vat-rates/{country}` | authenticated | Code or name; 404 when not recognised or without a rate |
| `PUT /api/v1/admin/vat-rates/{countryCode}` | `ROLE_ADMIN` | `{countryName, rate}`; rate 0-50 with at most 2 decimals; code must be 2 letters (`UK` is stored as `GB`); 201 created, 200 updated, 400 validation |
| `GET /api/v1/cart/tax-preview?country=PL` | authenticated | `{country, rate, taxAmount, netAmount, totalAmount}` for the cart total after any coupon; 400 without `country`, 404 unknown country |
| `GET /api/v1/currencies` | authenticated | `[{code, name, symbol, rateToUsd, updatedAt}]` by code; `rateToUsd` is the USD value of one unit |
| `GET /api/v1/currencies/convert?amount=&from=USD&to=EUR` | authenticated | `{amount, from, to, converted, rate}`; `from` defaults to USD; `converted` uses the exact ratio rounded half-up to 2 decimals, `rate` is rounded half-up to 6 decimals; 400 for a missing, negative or non-numeric amount, more than 2 decimals, or a malformed code; 404 unknown currency |
| `PUT /api/v1/admin/currencies/{code}` | `ROLE_ADMIN` | `{name, symbol, rateToUsd}`; rate greater than 0 with at most 6 decimals; USD must stay 1; 201 created, 200 updated, 400 validation |

Seeded currencies (fixed demo rates): USD 1, EUR 1.08, PLN 0.25, GBP 1.27,
CHF 1.13.

## Tier 2: warehouse fulfilment (warehouse-service)

Shipments, returns (RMA), suppliers, purchase orders and reorder suggestions
are implemented by `warehouse-service`, which keeps the public paths
(`/api/v1/warehouse/**`, `/api/v1/orders/{id}/shipment`, the returns,
suppliers, purchase-order and reorder-suggestion endpoints) behind the gateway.
Its business rules are unchanged; they are documented in that repository.

The backend keeps what warehouse-service builds on:

- `ROLE_WAREHOUSE` and the `warehouse` demo user.
- Inventory movement types `PURCHASE_RECEIVED` and `RETURN_RESTOCKED`, written
  by `InventoryService.receive`, which also triggers back-in-stock emails.
- Email templates `ORDER_SHIPPED`, `ORDER_DELIVERED` and `RETURN_UPDATED`.
  The backend renders them and publishes them as `GENERIC`, so older JMS
  consumers keep working.
- The order's fulfilment state and the internal API below, which
  warehouse-service calls to read orders and products, mirror shipment
  progress, cancel orders, receive stock and email customers.

### Order fulfilment state

The backend does not store shipments. It mirrors warehouse progress on the
order in `orders.fulfilment_state` (migration V8), exposed additively as
`fulfilmentState` in `OrderDto` (`NONE` for orders written before the column
existed):

```
       release         pick        pack         ship            deliver
NONE ─────────> AWAITING_PICK ──> PICKED ──> PACKED ──> SHIPPED ─────────> DELIVERED
                     │              │
                     └── cancel ────┴──> CANCELLED
```

| Rule | Result |
| --- | --- |
| Customer or admin cancellation while `NONE` or `AWAITING_PICK` | Allowed; stock restored, coupon released, state becomes `CANCELLED` |
| Customer or admin cancellation from `PICKED` on | 409 `Order is already being packed and cannot be cancelled` |
| Legacy `PUT /api/v1/orders/{id}/status` while `AWAITING_PICK` through `SHIPPED` | 409 |
| Cancelling a `SHIPPED` or `DELIVERED` order | 400, as before |

REST and GraphQL order mutations share `OrderService`, so they follow the same rules.

## Internal API for warehouse-service

Base path `/internal/v1`, called by warehouse-service (and, for payment
endpoints, payment-service) and not routed by the gateway. Every request
carries `X-Service-Token`, compared in constant time with the token of each
caller the endpoint accepts:

| Caller | Property | Environment variable | `local` default |
| --- | --- | --- | --- |
| warehouse | `internal.service-tokens.warehouse` (alias `warehouse.service-token`) | `WAREHOUSE_SERVICE_TOKEN` | `local-warehouse-token` |
| payment | `internal.service-tokens.payment` | `PAYMENT_SERVICE_TOKEN` | `local-payment-token` |

All endpoints below accept only the warehouse token. `InternalApiConfig.internalRoutes()`
maps path patterns to callers, so a payment endpoint is declared there with
`.route("/internal/v1/orders/\\d+/payment", ServiceCaller.PAYMENT)`.

| Situation | Result |
| --- | --- |
| No token configured for any caller the path accepts | 404 `{"message": "Not found"}` |
| Missing `X-Service-Token`, a wrong one, or another caller's token | 401 `{"message": "Invalid service token"}`, even with a valid JWT |

The internal API needs no JWT, gets no CORS headers, is not captured by the
traffic monitor or logged by Logbook (`X-Service-Token` is also obfuscated),
and is absent from `/v3/api-docs`.

| Method and path | Behaviour |
| --- | --- |
| `GET /orders/{id}` | `InternalOrderDto`, 404 when unknown |
| `GET /orders?status=PAID&fulfilment=NONE&page=0&size=20` | `PageDto<InternalOrderDto>`, oldest first; `status` defaults to `PAID`, `fulfilment` to `NONE`, negative pages become 0, size is clamped to 1–100 |
| `GET /orders?ids=1,2` | `List<InternalOrderDto>` of the given orders in any status, by id ascending; unknown ids are omitted; more than 100 ids is 400. warehouse-service uses it to follow customer cancellations |
| `PUT /orders/{id}/fulfilment` `{state}` | Next forward step only. `AWAITING_PICK` requires a `PAID` order in `NONE`. `SHIPPED` and `DELIVERED` also set the order status. Repeating the current state returns 200 without changes, including `AWAITING_PICK`, so a release whose response was lost can be retried (warehouse-service's unique shipment-per-order constraint prevents duplicates). Skips, backward steps and steps after `CANCELLED` are 409. `NONE` or `CANCELLED` in the body is 400 |
| `POST /orders/{id}/refunds` `{requestId, amount, reference}` | Books a refund for returned items. Only `DELIVERED` orders (409), amount 0.01 or more with at most 2 decimals (400), and the refunds of an order may not exceed its `totalAmount` (409). Adds the amount to `orders.refunded_amount` (shown as `refundedAmount` in order responses once it is above zero). A refund that brings the order to its full total releases the coupon redemption. Repeating a `requestId` with the same order and amount returns the order unchanged; a different order or amount is 409 |
| `POST /orders/{id}/warehouse-cancel` | Only for `PAID` orders in `AWAITING_PICK` or `PICKED` (otherwise 409); restores stock, releases the coupon, sets status and state to `CANCELLED` |
| `GET /products[?ids=1,2]` | `List<InternalProductDto>` by id ascending; unknown ids are omitted |
| `POST /inventory/receipts` | `{movementIds}` in request-line order. `type` must be `PURCHASE_RECEIVED` or `RETURN_RESTOCKED` (400), each product at most once (400), quantities ≥ 1, unknown product or `orderId` 404. Each movement stores the `requestId`, `reference` as its reason and actor `warehouse-service`. Repeating a `requestId` with the same type and lines returns the original ids without changing stock; different lines or type is 409 |
| `POST /notifications` | 202. `requestId` is required (400); `template` must be `ORDER_SHIPPED`, `ORDER_DELIVERED`, `RETURN_UPDATED`, `SHIPMENT_OUT_FOR_DELIVERY` or `SHIPMENT_DELIVERY_FAILED` (400) and its properties must be present (400: `orderId`, `carrier`, `trackingNumber` / `orderId` / `returnId`, `orderId`, `status`, optional `refundAmount` and `note` / `orderId`, `carrier`, `trackingNumber` for both carrier templates); unknown username 404. The backend renders the subject and body (`CustomerEmailRenderer`) and queues the email through the existing pipeline. A repeated `requestId` is accepted without a second email |
| `GET /jwks.json` | The RS256 access-token verification key as a JSON Web Key Set (`kty`, `use`, `alg`, `kid`, `n`, `e`) |

```text
InternalOrderDto   { id, username, status, fulfilmentState, subtotalAmount, discountAmount,
                     totalAmount, couponCode, refundedAmount, shippingAddress {street, city, state, zipCode, country},
                     items [{productId, productName, category, quantity, unitPrice}],
                     createdAt, updatedAt }
InternalProductDto { id, name, category, price, stockQuantity }
receipt body       { requestId, type, orderId?, reference, lines [{productId, quantity}] }
refund body        { requestId, amount, reference }
notification body  { requestId, username, template, properties {string: string} }
```

## Commerce extension points

These seams let payments, loyalty and gift cards, VAT and currency, and
webhooks build on the shop without changing existing behaviour.

### Domain events

`com.awesome.testing.event.CommerceEvent(id, type, username, orderId,
occurredAt, data)` is published with `ApplicationEventPublisher` inside the
transaction that changed the order; consumers use
`@TransactionalEventListener(phase = AFTER_COMMIT)`. Publish new events through
`CommerceEvents.publish(type, order[, extraData])`, which fills `data` with
`orderId`, `status` and `totalAmount`.

| Type | Wire name | Published when |
| --- | --- | --- |
| `ORDER_CREATED` | `order.created` | Checkout creates an order (not on an idempotent replay) |
| `ORDER_PAID` | `order.paid` | Admin status change to `PAID` (payment results later) |
| `ORDER_CANCELLED` | `order.cancelled` | Any cancellation: customer, admin, or warehouse |
| `ORDER_SHIPPED` | `shipment.shipped` | Admin status change to `SHIPPED` or fulfilment reaching `SHIPPED` |
| `ORDER_DELIVERED` | `shipment.delivered` | Admin status change to `DELIVERED` or fulfilment reaching `DELIVERED` |
| `REFUND_BOOKED` | `refund.booked` | A new refund is booked (`data` adds `amount` and `requestId`) |
| `PAYMENT_FAILED` | `payment.failed` | Reserved for payment results |

Repeating the current status or fulfilment state, replays, and rejected
changes publish nothing.

### Checkout and cart contributors

- `CheckoutContributor` beans run in `@Order` inside `OrderService.createOrder`
  after the items and coupon are priced and before the order is saved. They see
  `CheckoutContext(username, address, cartItems, order)` and may set their own
  order fields or reject checkout with a `CustomException`.
- `CartSummaryContributor` beans run in `@Order` when a cart summary is built
  and see `CartSummaryContext(username, cartItems, subtotal, discount, cart)`.
  Fields they set must be `@JsonInclude(NON_NULL)` on `CartDto` and set only
  when they apply, so other carts keep their exact legacy body.
- `OrderAmounts.amountDue(order)` is the amount a card payment must cover;
  today it equals `totalAmount`.

`VatCheckoutContributor` (`@Order(100)`) is the registered checkout
contributor; no cart summary contributors are registered.
