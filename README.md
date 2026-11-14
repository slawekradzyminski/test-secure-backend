# Secure Backend Application

This is a secure backend application built with Spring Boot, featuring JWT authentication, PostgreSQL database, and
ActiveMQ messaging.

## Container image

The published image is a GraalVM native executable (`Dockerfile`; the JVM build stays available as
`Dockerfile.jvm`). Native images fix bean selection at build time, so every profile shares one bean
graph and profiles only change properties: GraphQL and gRPC are always on, `app.email.transport`
chooses Artemis (`jms`) or the in-memory local outbox (`local-outbox`), and the bootstrap/seed
runners check their properties at runtime. Set the heap with a runtime argument, for example
`docker run backend -Xmx384m`. See `docs/NATIVE_IMAGE_SPIKE.md`.

## Release pipeline

Pushing a `vX.Y.Z` tag runs `.github/workflows/publish-image.yml`:

1. `validate` checks that the commit is on `master` and that the tag matches the project version.
2. `check-prior-verification` reads the git tree of the tagged commit and compares it with
   the trees of the commits behind the last 100 successful `verify.yml` runs (GitHub API,
   `actions: read`). PRs are squash-merged, so the tag commit is new but its tree is the
   tree of the PR head that passed CI (when the branch was up to date with `master`), or
   of the `master` push whose run has already finished.
3. When a matching run exists, `unit-tests`, `integration-tests`, `verify-local` and
   `verify-docker` are skipped and the run summary links the CI run that verified the tree.
   Otherwise they run as before. Manual (`workflow_dispatch`) prereleases always run them,
   and any API error falls back to the full verification. A failed or cancelled
   verification job still blocks the release.
4. `build` compiles the native image on an amd64 and an arm64 runner, pushes each by
   digest and runs `scripts/native-smoke.sh` against that pushed image on both paths;
   `publish` merges the digests into the tagged manifest lists.

A tag whose tree already passed `verify.yml` publishes in about 14–16 minutes instead of 27.

The release does not reuse an image from the PR: `verify.yml` never builds the native
image, and the published image must carry the release commit in its labels and be built
on both architectures, so promoting a PR image would need PR runs with `packages: write`.

## Profiles

The application supports three main profile modes:

### Local Profile

The local profile uses PostgreSQL on `localhost:5432/testdb` (user and password `postgres`) with the same
Flyway migrations as production, and runs without ActiveMQ (emails go to the local outbox). Start a database and the
backend with:

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Override the database with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.
No packaged profile uses H2 any more; it remains a test-scope dependency for the fast unit-test suite, while the
`*IT` suites and `RuntimeProfilesPostgresIT` run against PostgreSQL in Testcontainers.

This profile enables demo seed data automatically.

### Docker Profile

The Docker Compose setup uses PostgreSQL and includes the demo seed profile so it behaves like the lightweight local experience.

To run with Docker profile:

```bash
docker compose up --build
```

This starts the backend with `docker,demo`, so it loads the demo users, sample products, and sample orders into PostgreSQL.

### Demo Seed Profile

When you need the same seeded PostgreSQL-backed experience without Docker Compose, add the `demo` profile on top of `docker`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker,demo
```

Only `local` and `demo` enable startup seed data.

### Public Admin Bootstrap

Public deployments should create one explicit admin account through environment-backed configuration instead of demo seed data.

Required variables:

```bash
APP_BOOTSTRAP_ADMIN_ENABLED=true
APP_BOOTSTRAP_ADMIN_USERNAME=admin
APP_BOOTSTRAP_ADMIN_PASSWORD=<16+ character secret>
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
```

When enabled, startup creates that admin if it does not already exist. If the configured username/email already belongs to a non-admin user, startup fails instead of silently mutating the account.

### Public Product Bootstrap

Public deployments can also bootstrap a safe baseline product catalog without re-enabling demo users or sample orders.

Required variable:

```bash
APP_BOOTSTRAP_PRODUCTS_ENABLED=true
```

Optional override:

```bash
APP_BOOTSTRAP_PRODUCTS_CATALOG=classpath:bootstrap/products.json
```

When enabled, startup inserts the versioned catalog only when the `products` table is empty. If products already exist, bootstrap skips creation. This keeps clean demo resets deterministic without reintroducing local/demo seed users.

This will start:

- Backend service on port 4001
- Frontend service on port 8081
- PostgreSQL database on port 5432
- ActiveMQ on ports 61616 (broker) and 8161 (web console)

## Database Access

### Accessing PostgreSQL Database

You can interact with the PostgreSQL database in several ways:

1. Using docker exec and the psql command-line tool:

```bash
# Connect to the database
docker exec -it test-secure-backend-postgres-1 psql -U postgres -d testdb

# Common psql commands:
\dt                 # List tables
\d table_name       # Describe table
\q                  # Quit psql

# Example queries:
SELECT * FROM app_user;
SELECT * FROM products;
SELECT * FROM cart_items;
SELECT * FROM orders;
```

2. Using external tools:
    - Host: localhost
    - Port: 5432
    - Database: testdb
    - Username: postgres
    - Password: postgres

### Database Schema

The main tables in the database:

- `app_user`: Stores user information
- `email_event`: Stores per-user email delivery metadata for the app-owned verification endpoint
- `products`: Stores product catalog
- `cart_items`: Stores shopping cart items
- `orders`: Stores order information

## API Documentation

The API documentation is available at:

- Swagger UI: [http://localhost:4001/swagger-ui/index.html](http://localhost:4001/swagger-ui/index.html)
- OpenAPI JSON: [http://localhost:4001/v3/api-docs](http://localhost:4001/v3/api-docs)
- OpenAPI YAML: [http://localhost:4001/v3/api-docs.yaml](http://localhost:4001/v3/api-docs.yaml)

The Swagger UI definition dropdown has four entries: the backend document as JSON and as YAML,
"Warehouse service", the `warehouse-service` document that the `awesome-localstack` gateway serves at
`/warehouse/v3/api-docs`, and "Payment service", the `payment-service` document served at
`/payment/v3/api-docs`. Those two entries only load through the gateway (for example
`http://localhost:8081/swagger-ui/index.html`); on port 4001 they return 404. Warehouse and payment operations
use the same bearer token as the backend: sign in, then authorize again after switching definitions.

## Initial Data

Demo seed data is available only in `local` or `docker,demo` runs. Public/server deployments should keep it disabled.

- Admin users (username/password):
    - admin/LocalDemoAdmin123!
- Client users:
    - client/client
    - client2/client2
    - client3/client3
- Sample products in various categories
- Public-safe deployments can bootstrap only the product catalog through `APP_BOOTSTRAP_PRODUCTS_ENABLED=true`

Public deployments must not rely on these credentials.

## Security

The application uses JWT tokens for authentication. To access protected endpoints:

1. Get a token using the `/api/v1/users/signin` endpoint
2. Include the token in the Authorization header: `Bearer <token>`

Sign in responses now include both an access token and a refresh token. The refresh token can be exchanged via `POST /api/v1/users/refresh` even when the access token expires, and calling `POST /api/v1/users/logout` revokes the refresh token on the server.

Access tokens are signed with RS256. Production deployments must provide `JWT_SIGNING_KEY`, a base64 PKCS#8 RSA private key of at least 2048 bits (for example `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n'`; PEM armour is also accepted, but not the PKCS#1 that `genpkey -outform DER` writes with OpenSSL 3), and set `JWT_REQUIRE_SECURE_KEY=true`. The application then refuses to start without a valid key. Without `JWT_SIGNING_KEY` (local development and tests) the backend generates an ephemeral key at startup, so access tokens stop validating after a restart and clients refresh them with their refresh token. Rotating the key has the same effect. The public key is published as a JSON Web Key Set at `/internal/v1/jwks.json` (service-token protected, like the rest of the internal API), so `warehouse-service` verifies tokens without holding any signing secret.

Token revocation: every access token carries the account's token version (`ver` claim). Logout (`POST /api/v1/users/logout`, all devices) and a password reset increment it and delete all refresh tokens, so every earlier access token is rejected with `401 Invalid or expired token` on its next use of this backend (HTTP, STOMP traffic subscription and gRPC). Tokens issued before 3.20.0 have no claim and count as version 0. `warehouse-service` and `payment-service` verify tokens offline against the JWKS and cannot see the version: there a revoked access token stays usable until it expires (`security.jwt.token.expire-length`, 1 hour by default). Their exposure is limited to their own role-gated endpoints (warehouse: `ROLE_WAREHOUSE`/`ROLE_ADMIN` fulfilment and admin purchasing/returns; payment: client payments and admin payment views), and no new token can be obtained after revocation because the refresh tokens are gone. 2FA enable and disable still revoke only refresh tokens.

`warehouse-service` calls the internal API under `/internal/v1` with an `X-Service-Token` header instead of a JWT. Set the shared secret with `WAREHOUSE_SERVICE_TOKEN` on both services. When the variable is unset, the internal API responds with 404. The `local` profile defaults to `local-warehouse-token`. Each caller has its own token (`internal.service-tokens.warehouse` / `WAREHOUSE_SERVICE_TOKEN`, `internal.service-tokens.payment` / `PAYMENT_SERVICE_TOKEN`, local default `local-payment-token`), notification-service uses `internal.service-tokens.notification` / `NOTIFICATION_SERVICE_TOKEN` (local default `local-notification-token`) for the feed, the JWKS and the one-time webhook export `GET /internal/v1/webhooks/export`, and each internal endpoint accepts only its own callers' tokens. `WEBHOOKS_ENABLED=false` stops webhook delivery in the backend and `NOTIFICATIONS_DELIVERY=feed` turns commerce emails into `notification.requested` feed events for the notification-service (both default to the old behaviour). Both services poll the durable commerce event feed `GET /internal/v1/events`; the backend makes no outbound calls to them. See [docs/COMMERCE_AND_FULFILMENT.md](docs/COMMERCE_AND_FULFILMENT.md#internal-api-for-warehouse-service).

Users can subscribe signed webhooks to their order events (`/api/v1/webhooks`, with a built-in `inbox://` receiver and SSRF protection). See [docs/WEBHOOKS.md](docs/WEBHOOKS.md).

Password sign-in is the only login method. The OIDC/Keycloak single sign-on bridge (`POST /api/v1/users/sso/exchange`) was removed in 3.18.0. Accounts it provisioned remain ordinary users: they never had a usable local password, so they recover access through the password reset (`POST /api/v1/users/password/forgot`). The unused `app_user.auth_provider` and `provider_subject` columns stay in the schema, and a leftover `APP_SSO_*` environment variable is ignored.

## Features

- User authentication with JWT tokens
- Role-based authorization (ADMIN and CLIENT roles)
- User management (signup, signin, edit, delete)
- Email sending functionality via ActiveMQ
- Ollama integration for AI text generation and chat
- Product management
- Shopping cart functionality
- Order management, with stock reservations that expire (`ORDERS_RESERVATION_TTL`, default 30 minutes; see [docs/COMMERCE_AND_FULFILMENT.md](docs/COMMERCE_AND_FULFILMENT.md#stock-reservations-with-a-time-limit))
- Swagger/OpenAPI documentation
- Comprehensive test coverage

## Correlation IDs and logging

- `CorrelationFilter` runs first on every request: a valid incoming `X-Request-Id`
  (1-128 characters of `[A-Za-z0-9._-]`) and the trace id of a valid W3C `traceparent`
  are kept, anything missing or invalid is generated (UUID request id, random trace id),
  and every hop gets a new span id. The MDC holds `requestId`, `traceId` and `spanId`
  for the request; the response always carries `X-Request-Id` (exposed to browsers by CORS).
- Outbound calls send the same `X-Request-Id` and a child `traceparent` (same trace id,
  new parent id): the Ollama `WebClient` and webhook deliveries.
- Persisted hand-offs keep the ids (V17): feed events (`domain_events.request_id` /
  `trace_id`, served as `requestId` / `traceId`), email events and webhook deliveries
  (request id + traceparent). Delayed email sends and the email and webhook retry jobs
  log under the ids of the request that queued the work.
- Console and file logs show `[traceId,requestId]`. Set
  `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` for ECS JSON console logs, where the MDC entries
  are top-level `requestId`, `traceId` and `spanId` fields (works in the native image).

## Testing & Coverage

- Run `./mvnw verify` to execute the entire unit/integration suite. The build fails if line coverage drops below 40%, and JaCoCo reports are emitted to `target/site/jacoco/index.html`.
- Core scenarios are covered with focused unit tests for business services (users, products, carts, orders, email), security components (token provider, filter, authentication handler, security config), and controller utilities/exception handlers.
- Repository-specific behavior is validated with `@DataJpaTest` suites for `OrderRepository` and `CartItemRepository`, ensuring the custom JPQL queries behave correctly against H2 (unit tests); the `*IT` suites cover PostgreSQL.

## Getting Started

### Prerequisites

- Java 25 (Temurin distribution recommended)
- Maven Wrapper (bundled Maven 3.9.16)
- ActiveMQ (for email functionality)

### Running the Application

1. Clone the repository
2. Configure ActiveMQ connection in `application.yml`
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`

### Running Tests

```bash
mvn test
```

## API Endpoints

### Authentication

- POST `/api/v1/users/signin` - Authenticate user and get JWT token
- POST `/api/v1/users/signup` - Register a new client user
- POST `/api/v1/users/refresh` - Refresh JWT token using a refresh token
- POST `/api/v1/users/logout` - Revoke current refresh token and logout
- POST `/api/v1/users/password/forgot` - Anonymous endpoint that queues a password-reset email (always responds with 202)
- POST `/api/v1/users/password/reset` - Completes a reset using the emailed token and a new password (anonymous)

### User Management

- GET `/api/v1/users/me` - Get current user information
- GET `/api/v1/users/me/email-events` - Get the authenticated user's latest email events
- GET `/api/v1/users` - Get all users (ADMIN only)
- GET `/api/v1/users/{username}` - Get user by username
- PUT `/api/v1/users/{username}` - Update user (non-administrators get 403 for any username but their own)
- DELETE `/api/v1/users/{username}` - Delete user (ADMIN only)
- GET `/api/v1/admin/users/identity-issues` - Accounts that predate the username and email rules, for clean-up (ADMIN only)

Usernames use 4-255 letters, digits, `.`, `_` or `-` and are unique regardless of letter case; sign-in accepts
any case. Emails are stored lower-case and unique regardless of case. Passwords are 8 characters to 72 UTF-8 bytes
(the BCrypt limit). Request bodies are bound without type coercion: `1.5` or `"10"` is not an integer and `true`
is not a string; decimal amounts may be sent as numbers or strings.

### Products

- GET `/api/v1/products` - Get all products (authenticated)
- GET `/api/v1/products/{id}` - Get product by ID (authenticated)
- POST `/api/v1/products` - Create new product (ADMIN only)
- PUT `/api/v1/products/{id}` - Update product (ADMIN only)
- DELETE `/api/v1/products/{id}` - Delete product (ADMIN only)

### Shopping Cart

- GET `/api/v1/cart` - Get current user's cart
- POST `/api/v1/cart/items` - Add item to cart
- PUT `/api/v1/cart/items/{productId}` - Update item quantity
- DELETE `/api/v1/cart/items/{productId}` - Remove item from cart
- DELETE `/api/v1/cart` - Clear cart

### Orders

- POST `/api/v1/orders` - Create a new order
- GET `/api/v1/orders` - Get user's orders
- GET `/api/v1/orders/{id}` - Get order by ID
- PUT `/api/v1/orders/{id}/status` - Update order status (ADMIN only)
- POST `/api/v1/orders/{id}/cancel` - Cancel order
- POST `/api/v1/admin/orders/{id}/expire-reservation` - Expire a PENDING order's stock reservation now (ADMIN only, time control for tests)

New orders carry `reservationExpiresAt` (ISO-8601 UTC, milliseconds) in every order body; a PENDING order that is not paid by then is cancelled and its stock restored.

### QR Code

- POST `/api/v1/qr/create` - Generate QR code from text (authenticated)

### Email

- POST `/api/v1/email` - Send an email (authenticated users only)
- GET `/api/v1/users/me/email-events` - Inspect the authenticated user's latest email statuses without exposing Mailhog
- GET `/api/v1/local/email/outbox` *(local profile only)* - Inspect the in-memory email queue when running without Artemis
- DELETE `/api/v1/local/email/outbox` *(local profile only)* - Clear the local outbox buffer for a clean test run

Public-safe email verification is now app-owned rather than Mailhog-owned. For authenticated users, the
`/api/v1/users/me/email-events` endpoint returns only their own recent email metadata with statuses such as
`QUEUED`, `SENT_TO_SMTP_SINK`, or `FAILED`. Because this deployment uses Mailhog as a fake inbox, the backend
does not claim real-world inbox delivery; it only reports whether the message was queued and handed off to the
test mail sink.

#### Local Password Reset Flow

When running with the `local` profile the backend does not connect to Artemis. Instead, every outgoing `EmailDto`
payload is captured by the local outbox endpoint described above so that developers (or the frontend) can retrieve the
latest password-reset link without needing SMTP infrastructure. Each record contains the destination, payload, and a
timestamp. Clearing the outbox before a test run makes it easy to retrieve only the latest link. In Docker/localstack
profiles, reset messages are dispatched through Artemis to the dedicated JMS consumer which forwards them to Mailhog.

Every email message now carries a `template` identifier (e.g., `PASSWORD_RESET_REQUESTED`) and a `properties` map
containing contextual data such as the reset link, expiry window, and username. Downstream consumers can render their
own copy using those properties while the legacy `subject`/`message` fields remain populated for backward compatibility.

## Ollama Integration

The application integrates with Ollama to provide AI text generation and chat capabilities. These features are available
through secure endpoints that require authentication.

### Ollama Endpoints

- POST `/api/v1/ollama/generate` - Generate text using Ollama models
    - Single text generation without conversation history
    - Requires authentication with `ROLE_CLIENT` or `ROLE_ADMIN`
    - Supports Server-Sent Events (SSE) for streaming responses
    - Request body:
      ```json
      {
        "model": "qwen3.5:2b",
        "prompt": "Your prompt here",
        "options": {},
        "think": false
      }
      ```

- POST `/api/v1/ollama/chat` - Chat with Ollama models (stateless)
    - Supports multi-message conversations with history
    - Client maintains conversation history by sending all previous messages
    - Requires authentication with `ROLE_CLIENT` or `ROLE_ADMIN`
    - Supports Server-Sent Events (SSE) for streaming responses
    - Request body:
      ```json
      {
        "model": "qwen3.5:2b",
        "messages": [
          { "role": "system", "content": "You are a helpful assistant." },
          { "role": "user", "content": "Hello!" },
          { "role": "assistant", "content": "Hi there!" },
          { "role": "user", "content": "How are you?" }
        ],
        "options": {},
        "think": false
      }
      ```

- POST `/api/v1/ollama/chat/tools` - Chat with Ollama models and invoke backend functions (stateless)
    - Accepts the same conversation history as `/api/v1/ollama/chat` plus a `tools` array that describes available functions
    - Streams every chunk (assistant thinking, tool calls, tool results, and final reply) so workshop participants can watch the loop in real time
    - Currently exposes two functions:
        - `get_product_snapshot` – anchor every SKU answer with trusted JSON (price/stock/description). Small local models can hallucinate if they guess, so this is always the first hop when you are in the product lane.
        - `list_products` – grab a slice of the catalog right after a snapshot so the model can compare SKUs; keep it paired with `get_product_snapshot` to avoid invented cross-product claims.
      - We intentionally removed the Grokipedia lane, so every function call now focuses on internal inventory data.

    - Sample request:
      ```json
      {
        "model": "qwen3.5:2b",
        "messages": [
          { "role": "system", "content": "You are a helpful shopping assistant." },
          { "role": "user", "content": "How much does the Retro Console cost?" }
        ],
        "tools": [
          {
            "type": "function",
            "function": {
              "name": "get_product_snapshot",
              "description": "Return catalog metadata for a product so you can answer shopper questions accurately.",
              "parameters": {
                "type": "object",
                "properties": {
                  "productId": {
                    "type": "integer",
                    "description": "Numeric product id from the catalog."
                  },
                  "name": {
                    "type": "string",
                    "description": "Exact product name when the id is unknown."
                  }
                },
                "oneOf": [
                  { "required": ["productId"] },
                  { "required": ["name"] }
                ]
              }
            }
          }
        ]
      }
      ```
    - When the model decides to call `get_product_snapshot`, the backend executes `ProductService`, streams a `role: "tool"` payload containing the JSON snapshot (or `{ "error": "..." }`), and then resubmits the expanded history back to Ollama so the final assistant reply references the real data.
- GET `/api/v1/ollama/chat/tools/definitions` - Returns the JSON schema for every supported tool so SDKs/frontends can stay in sync with the backend contract (requires the same auth as the chat endpoints)

### Prompt Management

Each user can configure two system prompts that the backend injects automatically:

- GET/PUT `/api/v1/users/chat-system-prompt` – controls the general conversation tone for `/api/v1/ollama/chat`.
- GET/PUT `/api/v1/users/tool-system-prompt` – explains how to use the catalog tools for `/api/v1/ollama/chat/tools`.

Clients can fetch these endpoints to display/edit the prompts, but they no longer need to include the strings in the `messages` array; the controller prepends them before relaying any request to Ollama.

### Request Parameters

Both endpoints support the following parameters:

- `model` (required): The Ollama model to use (e.g., "qwen3.5:2b")
- `options` (optional): Model-specific options (e.g., temperature, max tokens)
- `think` (optional): Set to `true` for 'thinking' models that benefit from reasoning before responding. Defaults to `false`

For the `/generate` endpoint:
- `prompt` (required): The text prompt to generate from

For the `/chat` endpoint:
- `messages` (required): Array of conversation messages with role and content

### Configuration

The Ollama service can be configured in `application.yml`:

```yaml
ollama:
  base-url: http://localhost:11434  # Default Ollama server URL
```

## WebSocket Traffic Monitoring

The application includes a real-time HTTP traffic monitoring system implemented with WebSockets. This feature allows
tracking and visualization of all HTTP requests in the application.

### Features

- Real-time tracking of HTTP requests and responses
- Captures HTTP method, path, status code, response time, and timestamp
- Events are broadcast via WebSocket to connected clients
- Secured access requiring authentication

### Privacy

- Secrets are masked before traffic is stored or published, in every profile: values of JSON members, form
  and query parameters and headers whose names end in password, secret, token, code (a 2FA, recovery or gift
  card code; coupon and currency codes stay visible), signature, cvc or API key, plus `Authorization`,
  `Cookie` and service credential headers, and gift card codes in request paths. Only email masking is
  configurable (`APP_TRAFFIC_OBFUSCATE_EMAILS`).
- Every record keeps the user who made it. A caller reads only their own records, plus the anonymous records
  (for example a failed sign-in) of a traffic session bound to them: a session is bound to the first user
  seen on it. With `X-Client-Session-Id` the read is limited to that session.
- `APP_TRAFFIC_LEGACY_PUBLIC_ACCESS=true` (the server profile behind the AI Testers API course) keeps the
  endpoints public as before 3.19.0: an anonymous caller reads the records of the session in
  `X-Client-Session-Id` (list and detail), or every record without the header, and can subscribe to every
  event on `/topic/traffic`. Secrets stay masked. Authenticated callers follow the rules above.
- Traffic stored by an older release is masked with the current rules once, on the first start of a release
  with new rules (`TrafficHistoryMasker`, tracked in `traffic_masking_state`).

### Architecture

- Traffic is captured using a servlet filter (`TrafficLoggingFilter`)
- Events are stored in a thread-safe concurrent queue
- A scheduled publisher broadcasts events to WebSocket subscribers
- Uses STOMP protocol over WebSocket for messaging

### WebSocket Endpoints

- WebSocket Connection: `/api/v1/ws-traffic` (CONNECT with `Authorization: Bearer <token>` and
  `X-Client-Session-Id`)
- Subscription Topic: `/topic/traffic/{clientSessionId}`, only for the session's own user
- Data Format:
  ```json
  {
    "method": "GET",
    "path": "/api/v1/products",
    "status": 200,
    "durationMs": 45,
    "timestamp": "2023-03-22T10:15:30.123Z"
  }
  ```

### Usage

1. Connect to the WebSocket endpoint:
   ```javascript
   const socket = new SockJS('/api/v1/ws-traffic');
   const stompClient = Stomp.over(socket);
   
   // Include JWT token for authentication
   const headers = {
     'Authorization': 'Bearer ' + jwtToken
   };
   
   stompClient.connect(headers, function(frame) {
     // Subscribe to traffic events
     stompClient.subscribe('/topic/traffic', function(message) {
       const trafficEvent = JSON.parse(message.body);
       console.log('New traffic event:', trafficEvent);
       // Handle event (e.g., update UI)
     });
   });
   ```

2. Traffic events are automatically captured and broadcast as they occur in the application

### Visualization Tool

A simple HTML-based visualization tool is provided to help monitor traffic events in real-time:

1. Run the web server on port 8081 (this is CORS requirement, my frontend uses the same port - see `WebSecurityConfig`).
   Use absolute path

```
jwebserver -p 8081 -d /Users/slawek/IdeaProjects/test-secure-backend
```

2. Go to `http://localhost:8081/traffic-monitor.html`
3. Enter your server URL (default: http://localhost:4001)
4. Paste a valid JWT token (obtained from `/api/v1/users/signin` endpoint)
5. Click "Connect" to establish the WebSocket connection
6. Watch as HTTP traffic events appear in real-time

### API Endpoints

- GET `/api/v1/traffic/info` - Get WebSocket connection information (authenticated)
    - Returns:
      ```json
      {
        "endpoint": "/api/v1/ws-traffic",
        "topic": "/topic/traffic",
        "description": "WebSocket endpoint for real-time HTTP traffic events"
      }
      ```

### Test Strategy

The application follows a comprehensive testing strategy focusing on endpoint-level integration tests. Key aspects
include:

1. **Independent Endpoint Testing**
    - Each endpoint is tested in isolation
    - Tests are organized by feature in separate packages
    - All possible HTTP response codes are tested for each endpoint

2. **Test Organization**
    - Tests are ordered by HTTP status code (2xx first, then 4xx, 5xx)
    - Each test class focuses on a single endpoint functionality
    - Example test coverage for an endpoint:
        * 200/201 - Successful operations
        * 400 - Bad Request (invalid input)
        * 401 - Unauthorized (no authentication)
        * 403 - Forbidden (insufficient permissions)
        * 404 - Not Found (resource doesn't exist)

3. **Test Data Generation**
    - Uses factory pattern for test data creation
    - Factories generate random, valid test data using Faker
    - Located in `test/factory` package

4. **Test Structure**
    - Given/When/Then format for clear test organization
    - Descriptive test method names indicating the scenario
    - Comprehensive assertions for response status and body

Example test class organization:

```java
class SomeEndpointTest {
    void shouldReturnSuccessfully(); // 200 OK

    void shouldCreate(); // 201 Created

    void shouldGet400WhenInvalidInput();

    void shouldGet401WhenNoAuthorizationHeader();

    void shouldGet403WhenNotAuthorized();

    void shouldGet404WhenNotFound();
}
```

### AI Debugging Tips

When working with AI assistants, keep in mind:

1. Test failures may be caused by recent changes since git HEAD is kept stable. To see the changes use:
   ```bash
   git --no-pager diff
   ```

2. To run a single test and save the output to the testlogs folder, use JUnit notation:
   ```bash
   mvn test -Dtest=TestClassName#testMethodName > ./testlogs/test-output.log
   ```
   This helps in analyzing test failures by providing detailed logs.

3. The test logs can be read and analyzed by AI to help diagnose issues.

4. When making changes, always verify that all tests pass using:
   ```bash
   mvn test
   ```

## GraphQL commerce API

The automatically included `graphql` Spring profile exposes `/api/v1/graphql` for products,
carts, orders, and inventory. Customers can access their own carts and orders;
administrators can inspect all users' data and manage products and inventory.
Existing REST routes remain unchanged. See [the GraphQL guide](docs/GRAPHQL.md)
for permissions, schema examples, limits, and verification.

GraphiQL is enabled at `/api/v1/graphiql`. Add an access token in its Headers tab
for schema exploration and execution; see [GraphQL usage](docs/GRAPHQL.md#use-graphiql).

An optional `grpc` profile adds native admin inventory operations at
`127.0.0.1:9091`. See [gRPC usage](docs/GRPC.md) for the versioned schema,
authentication, statuses, deadlines, and idempotent retry examples.
