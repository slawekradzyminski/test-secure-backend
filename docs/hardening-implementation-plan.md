# Hardening Implementation Plan

## Objective

Harden the backend for a server-like deployment so obvious abuse paths are no longer unlimited.

The immediate goal is to reduce practical risk from:

- password brute force against `POST /api/v1/users/signin`
- account enumeration and email flooding through `POST /api/v1/users/password/forgot`
- reset-token guessing against `POST /api/v1/users/password/reset`
- refresh-token abuse against `POST /api/v1/users/refresh`
- expensive authenticated abuse against AI endpoints such as `/api/v1/ollama/**`

This plan is intentionally pragmatic. It fits the current codebase and single-server deployment model before introducing heavier distributed infrastructure.

## Current State

The backend currently has:

- JWT auth and refresh tokens
- public anonymous auth endpoints under `/api/v1/users/...`
- no rate limiting or throttling
- no temporary account/IP lockout for repeated failed sign-in attempts
- no explicit `429 Too Many Requests` response path
- no abuse-focused metrics or alerting

Relevant code today:

- [`WebSecurityConfig.java`](/Users/admin/IdeaProjects/test-secure-backend/src/main/java/com/awesome/testing/security/WebSecurityConfig.java)
- [`AuthenticationHandler.java`](/Users/admin/IdeaProjects/test-secure-backend/src/main/java/com/awesome/testing/security/AuthenticationHandler.java)
- [`UserService.java`](/Users/admin/IdeaProjects/test-secure-backend/src/main/java/com/awesome/testing/service/UserService.java)
- [`UserSignInController.java`](/Users/admin/IdeaProjects/test-secure-backend/src/main/java/com/awesome/testing/controller/users/UserSignInController.java)
- [`PasswordResetController.java`](/Users/admin/IdeaProjects/test-secure-backend/src/main/java/com/awesome/testing/controller/users/PasswordResetController.java)
- [`PasswordResetService.java`](/Users/admin/IdeaProjects/test-secure-backend/src/main/java/com/awesome/testing/service/password/PasswordResetService.java)

## Best-Fit Recommendation

### Recommended primary control

Implement backend-enforced rate limiting in this repository.

Best first implementation:

- Servlet filter or Spring MVC interceptor based
- route-aware policies
- in-memory counters backed by a bounded cache
- explicit `429 Too Many Requests` responses
- Micrometer metrics for allowed/blocked traffic

Best first storage fit:

- `caffeine` for bounded, expiring in-memory storage

Why this is the best first step for this project:

- no Redis or shared cache currently exists in the stack
- deployment is single-instance server-like, so in-memory enforcement is immediately effective
- protection lives with the backend even if nginx config drifts
- route-aware decisions are easier in application code than in generic edge config
- the codebase already uses Spring Boot + Micrometer, so observability integrates cleanly

### Recommended activation model

Keep the implementation present in code, but disabled by default.

Recommended behavior:

- default `security.rate-limit.enabled=false`
- do not change localhost behavior for `local`, lightweight Docker, or full local Docker
- enable hardening only in the server deployment through environment variables

This is important because the project is also used for training and load-testing exercises where localhost behavior should remain unrestricted unless explicitly changed.

### Recommended secondary control

Optionally add nginx edge throttling later in `awesome-localstack`.

That is useful as a coarse outer ring for:

- generic per-IP request bursts
- request floods that should be dropped before hitting Spring Boot

But nginx should not be the only protection because:

- it does not know usernames or reset identifiers
- it is harder to apply nuanced per-endpoint policies
- it is easier for app logic and docs to drift apart

## Security Goals

### Phase 1 goals

Protect the most sensitive anonymous endpoints first:

- `POST /api/v1/users/signin`
- `POST /api/v1/users/signup`
- `POST /api/v1/users/refresh`
- `POST /api/v1/users/password/forgot`
- `POST /api/v1/users/password/reset`

### Phase 2 goals

Protect expensive authenticated endpoints:

- `POST /api/v1/email`
- `POST /api/v1/qr/create`
- `/api/v1/ollama/**`

### Non-goals for the first pass

- perfect distributed rate limiting across multiple backend replicas
- bot mitigation with CAPTCHA
- WAF-style behavioral analysis
- full user-risk scoring

## Threat Model and Controls

### 1. Brute-force sign-in

Problem:

- attacker can repeatedly try passwords for one username from one or many IPs

Recommended controls:

- per-IP limit on sign-in attempts
- per-username limit on sign-in attempts
- combined `IP + username` limit for targeted brute force
- optional short cooldown after consecutive failed logins

Important point:

Only per-IP limiting is not enough. A distributed attacker can rotate IPs.

Only per-username limiting is also not enough. A malicious actor can grief a real user by exhausting that budget.

So the best shape is layered:

- coarse IP bucket
- tighter `IP + username` bucket
- moderate username bucket

### 2. Password reset flooding

Problem:

- `forgot` always returns success-like semantics, which is good for privacy, but it can still spam emails and fill JMS/Mailhog

Recommended controls:

- per-IP limit
- per-identifier limit
- minimum resend interval per existing user
- optional deduplication window for repeated identical requests

Important point:

This endpoint should remain privacy-preserving. Rate-limited responses must not reveal whether the account exists.

### 3. Reset-token guessing

Problem:

- attacker can repeatedly submit guessed reset tokens

Recommended controls:

- per-IP limit on `/password/reset`
- optional per-token-hash failure counter
- short lockout when repeated invalid-token attempts occur from one IP

### 4. Refresh-token abuse

Problem:

- automated hammering of `/refresh` can generate server load and noisy token churn

Recommended controls:

- modest per-IP limit
- optional per-refresh-token limit if needed later

### 5. Expensive endpoint abuse

Problem:

- Ollama and similar endpoints are much more expensive than CRUD endpoints

Recommended controls:

- lower throughput on `/api/v1/ollama/**`
- rate limits keyed to authenticated username when available
- fallback to IP for unauthenticated or rejected traffic

## Proposed Design

## 1. Add a dedicated request throttling module

Create a focused package, for example:

- `com.awesome.testing.security.ratelimit`

Likely classes:

- `RateLimitProperties`
- `RateLimitPolicy`
- `RateLimitDecision`
- `RateLimitKeyResolver`
- `RateLimitingService`
- `RateLimitingFilter` or `RateLimitingInterceptor`
- `RateLimitException`
- `RateLimitExceptionHandler`

Keep it isolated. Do not bury this logic inside controllers.

## 2. Use policy-driven route matching

Define explicit policies for selected endpoints instead of trying to rate-limit everything the same way.

Example conceptual policy table:

| Endpoint pattern | Identity key | Suggested purpose |
| --- | --- | --- |
| `/api/v1/users/signin` | IP, username, IP+username | brute-force defense |
| `/api/v1/users/signup` | IP | slow fake-account creation |
| `/api/v1/users/password/forgot` | IP, identifier | email/JMS abuse defense |
| `/api/v1/users/password/reset` | IP | token-guess defense |
| `/api/v1/users/refresh` | IP | refresh storm defense |
| `/api/v1/ollama/**` | username or IP | expensive compute defense |
| `/api/v1/email` | username or IP | abuse defense |
| `/api/v1/qr/create` | username or IP | flood defense |

Do not rate-limit:

- Swagger
- OpenAPI docs
- actuator health if needed by orchestration
- local email outbox endpoints in local profile

## 3. Use a bounded in-memory store first

Recommended backing store:

- `Caffeine` cache
- values are small per-key window state objects
- TTL tied to each policy window
- bounded `maximumSize` to avoid unbounded memory growth

Why this is acceptable now:

- current production-like deployment is a single VPS
- this gives immediate real protection against brute force
- operational complexity stays low

Known limitation:

- limits reset on backend restart
- limits do not coordinate across replicas

That is acceptable for the first server hardening pass.

## 4. Return proper `429` responses

When blocked:

- return HTTP `429 Too Many Requests`
- include a short, generic message
- include `Retry-After` when practical

Example response characteristics:

- no disclosure of whether a username exists
- no disclosure of exact internal thresholds
- enough information for frontend and operators to understand that throttling occurred

## 5. Add observability from day one

Add Micrometer counters and structured logs for:

- rate-limit allowed events
- rate-limit blocked events
- blocked endpoint
- blocked key type (`ip`, `username`, `ip_username`, `identifier`)

Suggested metrics:

- `security.rate_limit.allowed`
- `security.rate_limit.blocked`
- tags:
  - `endpoint`
  - `policy`
  - `key_type`

Do not log raw reset tokens or raw passwords.

For usernames and email identifiers, prefer:

- redaction
- hashing
- or partial masking in logs

## 6. Handle proxy-aware client IP extraction correctly

This matters in server mode because traffic comes through nginx.

The rate-limiter must not blindly trust arbitrary `X-Forwarded-For`.

Implementation requirement:

- centralize client-IP resolution
- trust forwarded headers only when the application is configured to run behind the known proxy setup
- otherwise fall back to `request.getRemoteAddr()`

The plan should align with current forwarded-header handling already used for Swagger/OpenAPI correctness.

## Proposed Policy Defaults

These numbers are starting points, not final truths. They are meant to be conservative without breaking classroom or demo usage.

### Anonymous auth endpoints

#### `POST /api/v1/users/signin`

- per IP: `20` requests per `5m`
- per username: `10` requests per `15m`
- per `IP + username`: `5` requests per `5m`
- optional cooldown after `5` consecutive failures for the same `IP + username`: `15m`

#### `POST /api/v1/users/signup`

- per IP: `10` requests per `1h`

#### `POST /api/v1/users/password/forgot`

- per IP: `10` requests per `15m`
- per identifier: `3` requests per `30m`
- resend floor for same existing user: at least `60s` between successful token creation

#### `POST /api/v1/users/password/reset`

- per IP: `10` requests per `15m`

#### `POST /api/v1/users/refresh`

- per IP: `60` requests per `15m`

### Expensive authenticated endpoints

#### `/api/v1/ollama/**`

- per authenticated username: `20` requests per `5m`
- fallback per IP: `20` requests per `5m`
- consider lower limits for tool-chat endpoints if they are materially heavier

#### `POST /api/v1/email`

- per authenticated username: `30` requests per `15m`

#### `POST /api/v1/qr/create`

- per authenticated username: `60` requests per `15m`

## Implementation Phases

## Phase 1: Foundation

### Deliverables

- add `bucket4j` dependency
- add `caffeine` dependency
- add typed configuration properties for throttling
- add centralized client identity resolver
- add a global `429` response path

### Notes

Keep the first slice intentionally small:

- sign-in
- forgot password
- reset password
- refresh

This gives the biggest security gain for the lowest change surface.

## Phase 2: Sign-in hardening

### Deliverables

- enforce sign-in limit before authentication attempt
- maintain combined `IP`, `username`, and `IP+username` decisions
- record failed login metrics

### Important design choice

Do not couple throttling to Spring Security internals more than necessary.

Prefer a pre-controller or pre-service check that consumes buckets and then calls the existing auth flow.

That keeps `AuthenticationHandler` focused on authentication and avoids mixing rate logic into credential validation itself.

## Phase 3: Password reset hardening

### Deliverables

- apply rate limits to `/password/forgot`
- add per-identifier throttling
- add resend floor for real users in `PasswordResetService`
- apply rate limits to `/password/reset`

### Important behavior requirement

`forgot` must preserve its current privacy-friendly external response.

Rate-limited requests should still avoid revealing whether the target account exists.

## Phase 4: Authenticated expensive endpoints

### Deliverables

- apply limits to `/api/v1/ollama/**`
- optionally apply limits to `/api/v1/email`
- optionally apply limits to `/api/v1/qr/create`

### Keying rule

If authenticated:

- key primarily by username

If anonymous or authentication failed before reaching controller:

- key by IP

## Phase 5: Operational hardening and rollout tuning

### Deliverables

- expose config via environment variables
- add dashboards or at least Prometheus query examples
- tune thresholds after observing real traffic

## Configuration Plan

Add a dedicated config section, for example:

```yaml
security:
  rate-limit:
    enabled: true
    trust-forwarded-headers: true
    cache:
      maximum-size: 10000
    policies:
      signin:
        path: /api/v1/users/signin
        ip:
          capacity: 20
          window: 5m
        username:
          capacity: 10
          window: 15m
        ip-username:
          capacity: 5
          window: 5m
      password-forgot:
        path: /api/v1/users/password/forgot
        ip:
          capacity: 10
          window: 15m
        identifier:
          capacity: 3
          window: 30m
```

Implementation detail:

- keep defaults safe for `docker` and `server`
- allow looser or disabled settings in `local` and `test` profiles if needed
- tests should still be deterministic

## Testing Strategy

## 1. Unit tests

Add focused tests for:

- key extraction logic
- forwarded-header handling
- bucket consumption behavior
- `Retry-After` calculation
- policy matching

## 2. MVC/integration tests

Add integration tests for:

- repeated failed sign-in attempts return `429`
- successful sign-in within limits still returns `200`
- forgot-password floods return `429` while keeping generic external message semantics
- reset-token brute force returns `429`
- refresh flood returns `429`

### Important

These tests must assert:

- status code
- stable error body shape
- no user-existence leakage

## 3. Metrics tests

At least one test should verify rate-limit metrics are recorded.

## 4. Non-regression tests

Verify existing happy paths still work:

- normal login
- signup
- password reset happy path
- refresh happy path
- Swagger/OpenAPI unaffected

## Rollout Strategy

## Recommended rollout

1. Implement Phase 1 to 3 in backend.
2. Release behind conservative defaults.
3. Keep the feature disabled by default in shared config.
4. Enable it only in the server deployment via environment variables.
5. Deploy to server.
6. Observe logs and metrics for a few days.
7. Tune thresholds.
8. Only then add nginx coarse edge throttling if still needed.

## Safe initial mode

If you want extra caution, add a temporary observe-only mode:

- compute rate-limit decisions
- log and meter would-block events
- do not enforce `429` yet

That is optional, but it can reduce rollout risk if you expect classroom load patterns to be noisy.

## Additional Hardening Worth Doing Soon

These are adjacent improvements, not blockers for the rate-limit work:

- tighten `/actuator/**` exposure in non-local profiles
- consider disabling or protecting Swagger in real internet-facing production
- add security event audit logs for login failure bursts
- add stronger secrets management for JWT secret and service credentials
- consider account-level temporary lock after repeated failures if abuse remains high

## Recommendation Summary

The best approach for this repository is:

1. implement backend-enforced, policy-driven rate limiting
2. keep it disabled by default in shared config
3. enable it only for server deployment through environment variables
4. use bounded in-memory storage for the first server hardening pass
5. protect anonymous auth endpoints first
6. add Micrometer metrics and structured logs from the start
7. optionally add nginx coarse per-IP throttling later as a second layer

This gives meaningful server hardening now without overengineering the stack.
