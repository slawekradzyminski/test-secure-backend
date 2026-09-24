# Native inventory gRPC API

This is an additional testing surface for four inventory operations, not a
whole-application performance optimization. The storefront continues to use REST
or GraphQL.

The optional `grpc` Spring profile adds a native HTTP/2 listener alongside REST
and the always-enabled GraphQL API. It uses the existing inventory service,
transactions, JWT issuer, current database roles, and movement audit records.
The listener is disabled by default. Inventory RPCs, health, and optional
reflection require an application bearer token belonging to an administrator.

## Run locally

```bash
SPRING_PROFILES_ACTIVE=local,grpc ./mvnw spring-boot:run
```

Defaults: address `127.0.0.1`, port `9091`, reflection disabled, maximum inbound
message size 64 KiB. Set `GRPC_HOST`, `GRPC_PORT`, or
`GRPC_REFLECTION_ENABLED=true` explicitly when needed. The listener uses plaintext
for local training. Keep it on loopback or a private network; public exposure
requires a separate HTTP/2 and TLS deployment design. The current HTTP nginx
routes do not proxy native gRPC. Browser clients still use REST or GraphQL.

The versioned schema is
[`src/main/proto/awesome/inventory/v1/inventory.proto`](../src/main/proto/awesome/inventory/v1/inventory.proto).
Java stubs are generated during Maven builds with the Spring Boot-managed
Protobuf plugin. This build uses Boot 4.1.0's native server starter and managed
gRPC/Protobuf versions; no additional third-party Spring starter is required.
See [Spring Boot gRPC documentation](https://docs.spring.io/spring-boot/reference/io/grpc.html).
Generated classes are excluded from PMD and coverage accounting; handwritten
adapters are included in the targeted PITest scope.

For Docker, use the workspace's optional `docker-compose.grpc.yml` override with
the pinned backend 3.8.0 image. It adds the `grpc` profile without replacing the
base file's active profiles and publishes only `127.0.0.1:9091`. See
[the workspace guide](https://github.com/slawekradzyminski/awesome-localstack/blob/main/docs/GRPC_INVENTORY.md).

## Authenticate and call

Obtain an administrator access token through the existing REST sign-in or SSO
flow. Set `ACCESS_TOKEN` in your shell without checking it into source control.
The commands below run from this backend repository and use
[grpcurl](https://github.com/fullstorydev/grpcurl). They work with reflection off.

```bash
grpcurl -plaintext -max-time 5 \
  -import-path src/main/proto -proto awesome/inventory/v1/inventory.proto \
  -H "authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"productId":"1"}' \
  localhost:9091 awesome.inventory.v1.InventoryService/GetStock

grpcurl -plaintext -max-time 5 \
  -import-path src/main/proto -proto awesome/inventory/v1/inventory.proto \
  -H "authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"page":0,"size":20,"status":"LOW_STOCK","lowStockThreshold":10}' \
  localhost:9091 awesome.inventory.v1.InventoryService/ListInventory

grpcurl -plaintext -max-time 5 \
  -import-path src/main/proto -proto awesome/inventory/v1/inventory.proto \
  -H "authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"productId":"1","delta":3,"reason":"Training restock","requestId":"550e8400-e29b-41d4-a716-446655440000"}' \
  localhost:9091 awesome.inventory.v1.InventoryService/AdjustStock

grpcurl -plaintext -max-time 5 \
  -import-path src/main/proto -proto awesome/inventory/v1/inventory.proto \
  -H "authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{"productId":"1","page":0,"size":20}' \
  localhost:9091 awesome.inventory.v1.InventoryService/ListStockMovements
```

Replace the product ID with an existing product. Use a fresh UUID for each new
adjustment. Repeat an uncertain adjustment with exactly the same product ID,
UUID, delta, and reason. An identical replay returns the original movement;
a changed payload is rejected. The same UUID may be used independently for a
different product. The authenticated username supplies the audit actor.

## Contract and errors

| Condition | gRPC status |
| --- | --- |
| Missing, malformed, expired token, or deleted user | `UNAUTHENTICATED` |
| Current user is not an administrator | `PERMISSION_DENIED` |
| Invalid ID, zero delta, blank/overlong reason, invalid UUID, pagination/filter | `INVALID_ARGUMENT` |
| Product does not exist | `NOT_FOUND` |
| Insufficient/overflowing stock or request-ID payload mismatch | `FAILED_PRECONDITION` |
| Message exceeds 64 KiB | `RESOURCE_EXHAUSTED` |
| Client deadline expires | `DEADLINE_EXCEEDED` |
| Unexpected application failure | `INTERNAL`, with no internal exception text |

Omitted page, size, and low-stock threshold default to 0, 20, and 10. Explicit
size zero or threshold zero is invalid. Page must be nonnegative; size is 1–100.
An absent stock status means no filter; an explicit unspecified or unknown enum
is invalid. Inventory is ordered by product ID; movements are newest first.
Optional order IDs, categories, reasons, and request IDs retain presence.
Date-time strings match the existing server-local REST timestamps; they are not
UTC instants. Protobuf JSON represents int64 identifiers as strings. Reserve
removed field numbers and names rather than reusing tags in this version.

Set a client deadline on every RPC. Calls already cancelled before dispatch do
not start an inventory operation. A deadline/cancellation during a database
transaction does **not** guarantee rollback: a write may commit after the caller
stops waiting. Reconcile with movement history or retry the identical adjustment
request ID; do not generate a new ID automatically. No automatic retries are
configured by the server. Streaming stock updates are outside this unary API.

The traffic viewer displays native RPC names, canonical statuses, durations,
and correlation IDs when `x-client-session-id` metadata is supplied. It never
stores message bodies, authorization metadata, or raw status descriptions.
See the [protocol lab](https://github.com/slawekradzyminski/awesome-localstack/blob/main/docs/PROTOCOL_TESTING_LAB.md). This adapter does not log raw messages, authorization metadata, or JWTs.
