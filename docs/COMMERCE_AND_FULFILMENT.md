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
| `ROLE_CLIENT` | Shopper. Coupons, gift cards, loyalty points, reviews, wishlist (returns and shipment tracking in warehouse-service). |
| `ROLE_WAREHOUSE` | Warehouse operator. Used by warehouse-service for the fulfilment queue and shipments. |
| `ROLE_ADMIN` | Everything, including coupons, gift cards, moderation and CSV (and the warehouse-service admin endpoints). |

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

### Gift cards

Admin endpoints under `/api/v1/admin/gift-cards` (`ROLE_ADMIN`); customers check
a card with `GET /api/v1/gift-cards/{code}` and apply it with
`PUT /api/v1/cart/gift-card {code}` / `DELETE /api/v1/cart/gift-card`.

| Method and path | Behaviour |
| --- | --- |
| `POST /api/v1/admin/gift-cards` `{amount, expiresAt?, recipientUsername?, note?}` | 201 `GiftCardDto` with a generated code `GC-XXXX-XXXX-XXXX` (alphabet without 0/O, 1/I/L). `amount` 1.00–1000.00 with at most 2 decimals (400); `expiresAt` must be in the future (400); unknown recipient 404. A recipient receives a `GIFT_CARD_ISSUED` email |
| `GET /api/v1/admin/gift-cards?status&page&size` | `PageDto<GiftCardDto>` newest first, optional effective-status filter |
| `GET /api/v1/admin/gift-cards/{code}` | `GiftCardDto` with `transactions` (ledger, oldest first) |
| `POST /api/v1/admin/gift-cards/{code}/disable` | 200; the balance is kept but cannot be spent. Already disabled 409 |
| `GET /api/v1/gift-cards/{code}` | `{code, balance, status, expiresAt}` only, never the recipient or note. Shares the per-user `gift-card-user` rate limit (30 per 15 minutes when rate limiting is enabled) with `PUT /api/v1/cart/gift-card` |

Codes are case-insensitive; a malformed code is 400, an unknown one 404. The
effective status is `DISABLED`, else `EXPIRED` (now ≥ `expiresAt`), else
`REDEEMED` (balance 0), else `ACTIVE`. Applying a disabled or expired card is
400, a card without balance 409, an empty cart 400. Every balance change locks
the card row and writes a `gift_card_transactions` row: `ISSUED`, `REDEEMED`,
`RESTORED` or `DISABLED`.

### Loyalty points

- `GET /api/v1/loyalty` → `{balance, lifetimeEarned, tier, valueOfBalance}` of the
  caller (zero for users who never earned). `tier` is informational: `BRONZE`,
  `SILVER` from 1000 lifetime points, `GOLD` from 5000.
- `GET /api/v1/loyalty/transactions?page&size` → the caller's ledger newest
  first: `EARNED`, `REDEEMED`, `RESTORED`, `REVOKED` with signed `points`.
- Earning: 1 point per full 1.00 of `totalAmount` when the order becomes `PAID`
  (an `ORDER_PAID` listener after commit, idempotent per order; a cancelled
  order earns nothing). The customer receives `LOYALTY_POINTS_EARNED`.
- Redemption: `PUT /api/v1/cart/loyalty-points {points}` in whole multiples of
  100 (400), at most the balance (409); `DELETE` removes them. 100 points = 1.00.

### Gift cards and points at checkout

- Tender order: the coupon discount (already in `totalAmount`), then the gift
  card, then points. Each part is capped so `amountDue` never drops below zero;
  points are capped to the cent, so `pointsRedeemed` can be below the applied
  multiple of 100 when the rest of the total needs less.
- The cart shows `giftCardCode`, `giftCardAmount`, `pointsRedeemed`,
  `pointsAmount` and `amountDue` only while a gift card or points are applied;
  otherwise its JSON is unchanged. A card that can no longer pay previews as 0.00.
  Clearing the cart drops both.
- Checkout locks and re-validates the card and the account. A disabled,
  expired, empty or deleted card, or too few points, is 409
  (`Applied gift card is no longer valid: <reason>`,
  `Applied loyalty points are no longer available: Insufficient loyalty points`),
  the checkout rolls back and the cart is kept. Otherwise the order stores
  `giftCardCode`, `giftCardAmount`, `pointsRedeemed`, `pointsAmount` (migration
  V12) and the cart selection is cleared. `OrderDto` shows them plus
  `amountDue = totalAmount − giftCardAmount − pointsAmount` only for such
  orders; they never change after checkout.
- Checkout still answers `PENDING`. When the tenders cover the whole total
  (`amountDue` 0), a listener marks the order `PAID` right after the checkout
  commits, which publishes `ORDER_PAID` and earns points as usual.
- Cancellation (customer, admin or warehouse) gives back the gift card amount
  and the points, and revokes the points the order earned (never below zero).
- Refunds (`REFUND_BOOKED`) are split by the payments `CardFirstRefundSplitter`
  (see "Card refunds"): the captured card payment first, then the gift card,
  then points, through `GiftCardRefundLegHandler` and
  `LoyaltyPointsRefundLegHandler`, which delegate to `TenderRefundService` and
  are idempotent per refund `requestId`. Independently, `TenderOrderEvents`
  revokes earned points in proportion to the refunded share of the total
  (rounded down, never below zero).

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

### Product search

`GET /api/v1/products/search` filters, sorts and pages the catalog and returns
facets; `GET /api/v1/products` stays a bare array. See
[PRODUCT_SEARCH.md](PRODUCT_SEARCH.md).

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

`InternalApiConfig.internalRoutes()` maps path patterns to callers. The payment
token is accepted by `POST /orders/{id}/payment` (payment token only),
`GET /orders/{id}` and `GET /jwks.json` (both tokens); every other endpoint
below accepts only the warehouse token.

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
| `POST /orders/{id}/payment` | Payment result from payment-service; see [Card payments](#card-payments) |

```text
InternalOrderDto   { id, username, status, fulfilmentState, subtotalAmount, discountAmount,
                     totalAmount, couponCode, refundedAmount, amountDue,
                     shippingAddress {street, city, state, zipCode, country},
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
| `ORDER_PAID` | `order.paid` | Admin status change to `PAID`, or a captured card payment (`data` adds `paymentId`) |
| `ORDER_CANCELLED` | `order.cancelled` | Any cancellation: customer, admin, or warehouse |
| `ORDER_SHIPPED` | `shipment.shipped` | Admin status change to `SHIPPED` or fulfilment reaching `SHIPPED` |
| `ORDER_DELIVERED` | `shipment.delivered` | Admin status change to `DELIVERED` or fulfilment reaching `DELIVERED` |
| `REFUND_BOOKED` | `refund.booked` | A new refund is booked (`data` adds `amount` and `requestId`) |
| `PAYMENT_FAILED` | `payment.failed` | payment-service reports a failed payment for a `PENDING` order (`data` adds `paymentId`, `failureCode`) |

Repeating the current status or fulfilment state, replays, and rejected
changes publish nothing.

User webhook subscriptions deliver these events to customer URLs; see
[WEBHOOKS.md](WEBHOOKS.md).

### Checkout and cart contributors

- `CheckoutContributor` beans run in `@Order` inside `OrderService.createOrder`
  after the items and coupon are priced and before the order is saved. They see
  `CheckoutContext(username, address, cartItems, order)` and may set their own
  order fields or reject checkout with a `CustomException`.
- `CartSummaryContributor` beans run in `@Order` when a cart summary is built
  and see `CartSummaryContext(username, cartItems, subtotal, discount, cart)`.
  Fields they set must be `@JsonInclude(NON_NULL)` on `CartDto` and set only
  when they apply, so other carts keep their exact legacy body.
- `OrderAmounts.amountDue(order)` is the amount a card payment must cover:
  `totalAmount − giftCardAmount − pointsAmount`. It is exposed as `amountDue`
  in `InternalOrderDto`.

Registered contributors: `VatCheckoutContributor` (`@Order(100)`), then
`TenderCheckoutContributor` and `TenderCartSummaryContributor` (`@Order(1000)`,
gift cards and loyalty points), which therefore see the final total.

## Card payments

payment-service (port 4004) charges cards and reports each final result to the
backend; the backend owns the order status. Public order bodies are unchanged:
payment details live in payment-service.

### Payment results

`POST /internal/v1/orders/{id}/payment` with `X-Service-Token` (payment token)
and `X-Payment-Signature: t=<epochSeconds>,v1=<hex>`, where `hex` is
HMAC-SHA256 of `t + "." + rawBody` keyed with `payment.webhook-secret`
(`PAYMENT_WEBHOOK_SECRET`). Body
`{requestId, paymentId, status: CAPTURED|FAILED, amount, currency?, failureCode?}`,
response `{orderId, orderStatus}`.

| Situation | Result |
| --- | --- |
| Missing or wrong service token | 401 `Invalid service token` |
| Missing signature, wrong secret, changed body, timestamp more than 5 minutes away, or no secret configured | 401 `Invalid payment signature` |
| Malformed JSON or invalid fields (`requestId`, `paymentId`, `status`, `amount` ≥ 0 with 2 decimals required; `currency` 3 upper-case letters) | 400 `{field: message}` |
| `FAILED` without `failureCode` | 400 |
| Unknown order | 404 |
| Repeated `requestId` with the same order, payment and status | 200 with the first answer, no side effects |
| Repeated `requestId` with anything else | 409 |
| `CAPTURED` for a `PENDING` order, amount equal to `amountDue` | 200 `PAID`; publishes `ORDER_PAID`, emails `PAYMENT_RECEIVED` |
| `CAPTURED` with another amount | 422, order unchanged |
| `CAPTURED` again for the payment that already paid the order (new `requestId`) | 200 with the current status |
| `CAPTURED` for a cancelled order, an order paid by another payment or by an admin | 409 `Order is not payable` (payment-service voids the capture) |
| `FAILED` | 200 with the unchanged status; for a `PENDING` order publishes `PAYMENT_FAILED` and emails `PAYMENT_FAILED` |

Final results are stored per order in `order_payments` (at most one
`CAPTURED`), handled notifications in `payment_notifications` (migration V11).

### Card refunds

When warehouse-service books a refund (`POST /internal/v1/orders/{id}/refunds`),
the `REFUND_BOOKED` event is split by `RefundSplitter` inside the same
transaction. `CardFirstRefundSplitter` sends the refund to the card up to the
captured amount minus card refunds already sent or queued, then offers the rest
to `RefundLegHandler` beans in `@Order`: `GiftCardRefundLegHandler` (`GIFT_CARD`)
gives back up to what the order paid with its gift card, then
`LoyaltyPointsRefundLegHandler` (`LOYALTY_POINTS`) up to the redeemed points'
value; anything beyond that is not sent anywhere. Orders without a captured
payment (paid by an admin) get no card leg, so the whole refund goes to the gift
card, then points.

The card part is written to `payment_refund_outbox` and delivered by
`PaymentRefundJob` to `POST {payment.base-url}/internal/v1/payments/refunds`
with the payment token and `{requestId, orderId, amount, reason}` (the booked
refund's requestId and reference).

Cancelling an order that has a captured card payment (customer, admin or
warehouse cancel, `ORDER_CANCELLED`) queues a card refund in the same outbox,
inside the cancelling transaction: the captured amount minus card refunds
already sent or queued, requestId `UUID.nameUUIDFromBytes("cancel:" + orderId)`
(queued at most once) and reason `Order cancelled`. Orders without a captured
payment (paid by an admin, or never paid) get none. In the same transaction the
gift card and points are restored in full and the points the order earned are
revoked (see "Gift cards and points at checkout").

| payment-service answer | Outbox row |
| --- | --- |
| 2xx `{refundId, ...}` | `SENT` with the refund id |
| 404 (no captured payment) | `SKIPPED`, nothing to refund on the card |
| 409, 400, 422 | `FAILED`, logged, not retried |
| 5xx, timeout, connection error, other 4xx | Retried after 30 s, doubling up to 30 min; `FAILED` after 10 attempts |

| Property | Environment variable | Default (`local`) |
| --- | --- | --- |
| `payment.base-url` | `PAYMENT_BASE_URL` | `http://payment-service:4004` (`http://localhost:4004`) |
| `payment.webhook-secret` | `PAYMENT_WEBHOOK_SECRET` | blank: every result is 401 (`local-payment-webhook-secret`) |
| `internal.service-tokens.payment` | `PAYMENT_SERVICE_TOKEN` | blank: payment caller disabled (`local-payment-token`) |
| `payment.refunds.delivery.enabled` | `PAYMENT_REFUNDS_DELIVERY_ENABLED` | `true` |
| `payment.refunds.delivery.interval` | `PAYMENT_REFUNDS_DELIVERY_INTERVAL` | `PT15S` |
