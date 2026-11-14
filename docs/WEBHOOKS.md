# Webhooks

Signed HTTP callbacks for a user's own order events. Any signed-in user can subscribe
a URL; every event is recorded as a delivery, sent with an HMAC signature, retried with
backoff and visible in a delivery log. A built-in inbox lets students receive webhooks
without running a server.

## Endpoints

All endpoints require a Bearer token and act on the caller's own resources.

| Method and path | Result |
| --- | --- |
| `GET /api/v1/webhooks/event-types` | `200 [{name, description}]` |
| `GET /api/v1/webhooks` | `200 [WebhookSubscriptionDto]`, oldest first |
| `POST /api/v1/webhooks` `{url, events[], description?}` | `201 WebhookSubscriptionDto` including `secret` (shown once) |
| `GET /api/v1/webhooks/{id}` | `200 WebhookSubscriptionDto` |
| `PUT /api/v1/webhooks/{id}` `{url, events[], description?, active?}` | `200 WebhookSubscriptionDto` |
| `DELETE /api/v1/webhooks/{id}` | `204`; deletes the delivery log too |
| `POST /api/v1/webhooks/{id}/rotate-secret` | `200 WebhookSubscriptionDto` including the new `secret` |
| `POST /api/v1/webhooks/{id}/test` | `201 WebhookDeliveryDto`; sends `webhook.ping` and waits for the result |
| `GET /api/v1/webhooks/{id}/deliveries?status&page&size` | `200 PageDto<WebhookDeliveryDto>`, newest first |
| `GET /api/v1/webhooks/{id}/deliveries/{deliveryId}` | `200 WebhookDeliveryDto` |
| `POST /api/v1/webhooks/{id}/deliveries/{deliveryId}/redeliver` | `201 WebhookDeliveryDto`; a new delivery of the same event |
| `POST /api/v1/webhook-inboxes` | `201 {token, url: "inbox://<token>", createdAt}` |
| `GET /api/v1/webhook-inboxes` | `200 [WebhookInboxDto]` |
| `GET /api/v1/webhook-inboxes/{token}/requests` | `200 [{id, receivedAt, headers, body}]`, newest first, last 50 kept |
| `DELETE /api/v1/webhook-inboxes/{token}` | `204` |
| `GET /api/v1/admin/webhooks?username&page&size` | `200 PageDto<WebhookSubscriptionDto>` (`ROLE_ADMIN`) |

Errors use `{"message": "..."}` (field validation uses a field-to-message map):

- `400` invalid body, unknown event type (`Unknown event type 'x'. Allowed: ...`), or a
  rejected URL (`Webhook URL rejected: Host 10.0.0.1 resolves to a private address; ...`).
- `401` no or invalid token; `403` the subscription belongs to another user (or a
  non-admin calls the admin list); `404` unknown subscription, delivery or inbox (other
  users' inboxes are also `404`).
- `409` more than 5 subscriptions or 3 inboxes, or `test`/`redeliver` on a disabled
  subscription.
- `429` too many `test`/`redeliver` calls, when rate limiting is enabled.

`WebhookSubscriptionDto`: `id, username, url, description, events[], active,
consecutiveFailures, disabledReason, secret (create/rotate only), createdAt, updatedAt`.

`WebhookDeliveryDto`: `id, subscriptionId, eventId, eventType, status
(PENDING|DELIVERED|FAILED), attempts, nextAttemptAt, lastStatusCode, lastError,
responseSnippet (first 500 characters), durationMs, payload, createdAt, deliveredAt`.

## Events and payload

Subscriptions name event wire names: `order.created`, `order.paid`, `order.cancelled`,
`shipment.shipped`, `shipment.delivered`, `refund.booked`, `payment.failed`,
`order.reservation-expired` (3.21.0; the commerce domain events, see
[COMMERCE_AND_FULFILMENT.md](COMMERCE_AND_FULFILMENT.md#domain-events)), plus `webhook.ping`.
`order.cancelled` data carries `reason` (`CUSTOMER`, `ADMIN`, `RESERVATION_EXPIRED`,
`WAREHOUSE` or `ACCOUNT_ERASURE`); an expired reservation sends both `order.cancelled` and
`order.reservation-expired` with data `{orderId, expiredAt, totalAmount}`. Each committed event creates one delivery per active subscription of
the order's owner that lists its type.

```json
{"id":"6a1f0f57-0f7c-4bb4-8a43-2f9d8f7e8c11","type":"order.created","createdAt":"2026-11-10T20:40:33.120Z","data":{"orderId":42,"status":"PENDING","totalAmount":40.00}}
```

The body is compact JSON with the fields in this order and `data` keys sorted, so the
same event always produces the same bytes. `id` is the event id: redeliveries keep it,
so receivers can deduplicate on it.

## Request and signature

```
POST <your path> HTTP/1.1
Content-Type: application/json
User-Agent: AwesomeTesting-Webhooks/1.0
X-Webhook-Id: <delivery id>
X-Webhook-Event: order.created
X-Webhook-Signature: t=1794346833,v1=5f2b...
X-Request-Id: <request id of the request that caused the event>
traceparent: 00-<trace id of that request>-<new parent id>-01
```

`X-Request-Id` and `traceparent` (3.17.0) correlate the delivery with the request that
caused it: the ids are stored with the delivery when it is queued, so retries and
redeliveries send the same request id and trace id (with a new parent id per attempt).
They are not signed; deduplicate on `X-Webhook-Id` / the event `id`, not on them.

`v1` is the lowercase hex HMAC-SHA256 of `t + "." + rawBody`, keyed with the whole secret
string as UTF-8 (including the `whsec_` prefix). Verify it against the raw body and
reject old timestamps, for example:

```python
expected = hmac.new(secret.encode(), f"{t}.{raw_body}".encode(), hashlib.sha256).hexdigest()
ok = hmac.compare_digest(expected, v1) and abs(time.time() - int(t)) < 300
```

Secrets are `whsec_` plus 32 random bytes (base64url). They are stored encrypted with the
MFA text encryptor (`APP_MFA_ENCRYPTION_PASSWORD` / `APP_MFA_ENCRYPTION_SALT`) and are
returned only by create and rotate-secret. Retries after a rotation use the new secret.

## Delivery, retries and auto-disable

- A delivery succeeds on a `2xx` response within 5 seconds (connect plus response).
  Redirects are never followed (`3xx` counts as a failure) and at most 4 KB of the
  response body is read.
- Failed attempts are retried by `WebhookRetryJob` after 1, 2, 4, ... minutes; after the
  8th failed attempt the delivery stays `FAILED`. Targets blocked by the SSRF guard and
  missing inboxes fail at once without retries.
- Every failed attempt increments the subscription's `consecutiveFailures`; a success
  resets it. At 20 the subscription is disabled with a `disabledReason`; its pending
  deliveries end `FAILED`. `PUT` with `active: true` re-enables it and resets the counter.
- Event deliveries are written in their own transaction after the order commits and are
  sent on a small background pool; anything not sent (restart, full queue) is picked up by
  the retry job. Concurrent senders claim a delivery before sending, so it is never sent
  twice at once.

## Built-in inbox

`POST /api/v1/webhook-inboxes` returns `inbox://<token>`. Subscriptions may target only
their owner's inboxes. Deliveries to an inbox skip HTTP but are otherwise identical: the
same headers and signature are stored with the body (up to 16 KB), the delivery log shows
status `200`, and the newest 50 requests per inbox are kept. A deleted inbox makes later
deliveries fail with `Webhook inbox not found`.

## SSRF protection

The site is public, so a webhook URL must never reach the backend's own network. The
same `WebhookUrlPolicy` runs when a subscription is created or updated (`400` with the
reason) and again immediately before every delivery (`FAILED` with `Blocked: <reason>`).

- Only `http` and `https` (http can be switched off), no user info, port 80 or 443 plus
  `webhooks.allowed-ports`.
- Host names must be fully qualified: `localhost`, `*.localhost`, `*.local`,
  `*.internal` and single-label names such as Docker service names (`backend`,
  `postgres`) are rejected, as are numeric forms like `2130706433`.
- The host must resolve, and every resolved address must be public. Rejected: loopback
  (`127.0.0.0/8`, `::1`), private (`10/8`, `172.16/12`, `192.168/16`), link-local
  (`169.254/16` including cloud metadata, `fe80::/10`), carrier-grade NAT (`100.64/10`),
  unspecified (`0/8`, `::`), multicast, broadcast, reserved and documentation ranges,
  IPv6 unique-local `fc00::/7` and site-local `fec0::/10`, Teredo, and IPv4 addresses
  embedded in IPv6 (`::ffff:a.b.c.d`, `::a.b.c.d`, NAT64 `64:ff9b::/96`, 6to4 `2002::/16`),
  which are judged by their IPv4 part.
- The delivery connects to exactly the addresses vetted for that attempt and sends the
  original host name as `Host` and TLS SNI, with normal certificate and host name
  verification. A DNS answer that changes after validation (rebinding) is therefore
  caught by the delivery-time check and cannot redirect the request.

`WebhookHttpSender` is a small HTTP/1.1 client over a socket because the JDK
`HttpClient` resolves host names itself and cannot be pinned to a vetted address. It uses
only JDK socket and TLS APIs, so the native image needs no extra reflection metadata.

## Configuration

| Property | Default | Meaning |
| --- | --- | --- |
| `webhooks.allow-http` (`WEBHOOKS_ALLOW_HTTP`) | `true` | Accept `http` targets |
| `webhooks.allowed-ports` (`WEBHOOKS_ALLOWED_PORTS`) | empty | Extra target ports, comma-separated |
| `webhooks.timeout` (`WEBHOOKS_TIMEOUT`) | `PT5S` | Deadline of one attempt |
| `webhooks.max-attempts` (`WEBHOOKS_MAX_ATTEMPTS`) | `8` | Attempts before `FAILED` |
| `webhooks.initial-backoff` (`WEBHOOKS_INITIAL_BACKOFF`) | `PT1M` | First retry delay, doubling |
| `webhooks.auto-disable-after` (`WEBHOOKS_AUTO_DISABLE_AFTER`) | `20` | Consecutive failures that disable a subscription |
| `webhooks.max-subscriptions-per-user` | `5` | Subscription limit |
| `webhooks.max-inboxes-per-user` | `3` | Inbox limit |
| `webhooks.inbox-request-limit` | `50` | Requests kept per inbox |
| `webhooks.async-dispatch` | `true` | Send event deliveries in the background (tests: `false`) |
| `webhooks.retry.enabled` (`WEBHOOKS_RETRY_ENABLED`) | `true` | Run the retry job (tests: `false`) |
| `webhooks.retry.interval` / `initial-delay` | `PT30S` | Retry job schedule |
| `webhooks.retry.batch-size` | `20` | Deliveries per retry run |
| `security.rate-limit.policies.webhook-test-user` | 10 per 15 min | `POST .../test` per user |
| `security.rate-limit.policies.webhook-redeliver-user` | 20 per 15 min | `POST .../redeliver` per user |

Rate limits apply only when `security.rate-limit.enabled=true`, like the other policies.

## Storage

`V14__webhooks.sql` creates `webhook_subscriptions`, `webhook_deliveries` (cascade on
subscription delete), `webhook_inboxes` and `webhook_inbox_requests` (cascade on inbox
delete). Deleting a user removes their subscriptions, deliveries, inboxes and requests.

## Testing

Tests replace DNS with `StubWebhookHostResolver` and use
`WebhookUrlPolicy.allowingLoopbackForTests`, which additionally accepts loopback
addresses on any port so a local WireMock server can act as the receiver; every other
rule still applies. Production wiring never uses that factory.
