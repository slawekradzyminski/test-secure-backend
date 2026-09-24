# Commerce GraphQL API

GraphQL is enabled by default at `POST /api/v1/graphql` alongside the
existing REST API. It covers products, carts, orders, and inventory. REST routes,
DTOs, authentication, and business services remain available unchanged.

## Run locally

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

The endpoint uses the existing application access JWT in `Authorization: Bearer
<access-token>`. Obtain it through the existing sign-in/SSO flow; GraphQL does not
introduce a second login system. The base configuration includes the `graphql`
Spring profile automatically, including when `SPRING_PROFILES_ACTIVE` selects
`local`, `docker`, or a deployment profile. No extra enablement flag is needed.

The schema lives in `src/main/resources/graphql-preview/commerce.graphqls`.
GraphiQL is enabled at `GET /api/v1/graphiql`. The existing nginx `/api/v1/`
route forwards both the editor and GraphQL requests without a new gateway rule.

For isolated Docker verification, see
[the workspace compatibility runner](https://github.com/slawekradzyminski/awesome-localstack/blob/main/docs/COURSE_COMPATIBILITY.md).
Backend 3.8.0 is pinned in the workspace deployment Compose files; see the
[release record](https://github.com/slawekradzyminski/awesome-localstack/blob/main/docs/GRAPHQL_GRPC_RELEASE.md).

## Swagger and GraphQL documentation

Swagger UI and `/v3/api-docs` continue to describe the REST API. Spring GraphQL
resolvers do not become separate OpenAPI operations: all GraphQL queries and
mutations use the single `POST /api/v1/graphql` transport endpoint. The existing
OpenAPI document is intentionally preserved for REST clients and course tests.

GraphQL's documentation is its typed schema, including field descriptions and
input types. Authenticated tools can discover it through introspection. A generic
Swagger entry could describe the JSON envelope (`query`, `variables`, and
`operationName`), but would not provide the schema-aware query editor or operation
discovery available in GraphiQL. You can also send authenticated GraphQL requests
from Postman or another GraphQL client.

References: [Swagger/OpenAPI](https://swagger.io/docs/specification/v3_0/about/)
and [Spring GraphiQL and introspection](https://spring.io/guides/topicals/observing-graphql-in-action).

## Use GraphiQL

Open `/api/v1/graphiql` on the backend or gateway origin. The editor redirects to
include `path=/api/v1/graphql`, so requests use the same origin through nginx.
Only the editor's GET route is public; introspection, queries, and mutations
still require the existing access token. In the editor's **Headers** tab, enter:

```json
{"Authorization": "Bearer <access-token>"}
```

Then run:

```graphql
query MyCart {
  cart { totalItems }
  products(limit: 5) { items { id name price } }
}
```

The schema explorer describes all operations; the server still applies customer
ownership and administrator permissions when executing them. Replace the header
when the access token expires; GraphiQL does not perform the storefront's automatic
refresh flow. The bundled Spring editor loads its JavaScript and styles from
`esm.sh`, so the browser needs access to that CDN. GraphiQL persists headers in
browser storage; clear the Headers tab when finished on a shared browser.

## Authorization

| Operation | Authenticated customer | Administrator |
| --- | --- | --- |
| `products`, `product` | Shared catalog and stock availability | Entire catalog |
| `cart` | Own cart; another username is forbidden | Own cart by default, or any explicit username |
| `carts` | Forbidden | Paginated list of nonempty carts |
| `orders` | Own orders only | All orders by default; optional username filter |
| `order` | Own order; another owner's ID is reported as not found | Any order |
| `inventory`, `inventoryItem`, `inventoryMovements` | Forbidden | Full inventory management data |
| Cart mutations | Own cart only | Own or explicitly selected user's cart |
| `checkout` | Own cart | Own cart |
| `cancelOrder` | Own order, subject to existing state rules | Any order, subject to existing state rules |
| Product mutations, `adjustInventory`, `updateOrderStatus` | Forbidden | Allowed |

Ownership comes from the validated application principal. Role/ownership checks
run in the application facades, not just the GraphQL controllers. Aliases,
fragments, and multiple root fields do not change the authorization rules.
Schema introspection describes the API; it does not grant access to its fields.

An absent or invalid access token is rejected at the HTTP security boundary.
Authenticated field failures use GraphQL errors, with the affected root field
set to null. A response can contain both permitted data and errors.

## Query examples

Customer shopping state:

```graphql
query Shopping {
  products(limit: 10, inStockOnly: true) {
    total
    items { id name price stockQuantity }
  }
  cart {
    username
    totalItems
    totalPrice
    items { quantity unitPrice product { id name imageUrl price } }
  }
  orders(size: 10) {
    total
    items { id status totalAmount createdAt }
  }
}
```

Administrator inspection:

```graphql
query CustomerAndInventory($username: String!, $productId: ID!) {
  cart(username: $username) { totalItems items { product { id name } quantity } }
  orders(username: $username) { total items { id status totalAmount } }
  inventory(size: 10) { total items { productId name availableQuantity stockStatus } }
  inventoryMovements(productId: $productId, size: 10) {
    total
    items { id type delta quantityAfter actor reason requestId }
  }
}
```

Supply a JSON envelope with `query`, optional `operationName`, and `variables`.
For example, the variables for the administrator query could be:

```json
{"username":"client","productId":"1"}
```

## Mutations

Cart operations are `addCartItem`, `updateCartItem`, `removeCartItem`, and
`clearCart`. Adding requires a positive quantity. Updating to zero removes an
item. Cart additions do not reserve stock; checkout performs stock validation
and deduction under the existing database locks.

```graphql
mutation AddAndInspect($productId: ID!) {
  addCartItem(productId: $productId, quantity: 2) {
    totalItems
    totalPrice
    items { product { id name } quantity unitPrice }
  }
}
```

`checkout(address: AddressInput!)` uses the authenticated user's current cart.
`cancelOrder(id: ID!)` preserves the existing cancellation and stock-restoration
rules. Order totals and line prices retain their purchase-time values.

Product administrators can use `createProduct`, `updateProduct`, and
`deleteProduct`. Product inputs apply the existing Bean Validation constraints.
For updates, omission and explicit null both leave a field unchanged, matching
the REST update semantics. Deleting a missing product returns false; references
that prevent deletion produce a conflict.

```graphql
mutation Restock($productId: ID!, $requestId: String!) {
  adjustInventory(productId: $productId, input: {
    delta: 3
    reason: "Training restock"
    requestId: $requestId
  }) {
    id
    quantityAfter
    actor
    requestId
  }
}
```

`requestId` is a UUID. Replaying the same product/request-ID pair with the same
delta and reason returns the original movement. Changing the payload produces
`CONFLICT`. The actor is taken from the authenticated principal.

Multiple mutation fields execute as separate application transactions. They
are not one atomic batch. Checkout and additive cart operations do not gain
idempotency merely by using GraphQL; do not automatically retry them after an
uncertain response.

## Wire formats and limits

- IDs use GraphQL `ID`; pass them as strings to avoid JavaScript integer loss.
- Money uses exact decimal strings, such as `"39.98"`. Counts and quantities
  use GraphQL integers.
- Dates are ISO-formatted server-local date-times without an offset, matching
  the existing domain timestamp semantics.
- Catalog pagination uses zero-based `offset` and `limit`. Other lists use
  zero-based `page` and `size`. All page sizes must be between 1 and 100.
- Request bodies are limited to 64 KiB, including requests without a declared
  content length. HTTP JSON-array batching is rejected.
- Query depth is limited to 20 (including schema introspection) and complexity to 1000. Complexity accounts for
  requested page sizes and aliases. Transactions use a five-second timeout;
  this is not an end-to-end network deadline.
- Product lookups for a page are batched. Cart projections fetch their products
  in the same query; admin cart listings batch across selected owners.

Business errors expose `errors[].extensions.code`: `BAD_REQUEST`, `FORBIDDEN`,
`NOT_FOUND`, or `CONFLICT`. Unexpected failures retain the framework's sanitized
internal error handling. Tests and clients must inspect `errors`, even when
the HTTP status is 200.

Raw GraphQL requests are excluded from Logbook capture. The existing REST
traffic-monitor UI displays sanitized GraphQL operation summaries, actual HTTP
status, execution outcome, and correlation IDs. Supply a valid explicit
`X-Client-Session-Id` to capture an operation; bodies, variables, aliases,
client operation names, and raw errors are never stored. See the
[protocol lab](https://github.com/slawekradzyminski/awesome-localstack/blob/main/docs/PROTOCOL_TESTING_LAB.md). The frontend now provides a per-tab
REST/GraphQL selector; see [the storefront guide](https://github.com/slawekradzyminski/vite-react-frontend/blob/main/docs/GRAPHQL_STOREFRONT.md).
gRPC, subscriptions, and public deployment remain planned.

## Verification

```bash
./mvnw verify
./mvnw -Pintegration-tests verify
./mvnw -Pmutation-testing test-compile pitest:mutationCoverage
```

GraphQL tests use Given/When/Then and exercise the real HTTP authentication
boundary. PostgreSQL integration tests race checkout for the final unit and
inventory retries for the same request ID. The workspace course runner checks
unchanged lesson tests against the built image, plus direct and proxied GraphQL
access. The semantic mutation lab independently checks owner scoping and admin
inventory policy.
