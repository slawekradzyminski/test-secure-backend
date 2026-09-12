# Controller OpenAPI audit — 2026-09-10

## Scope and evidence

Audited all **25 application HTTP controllers / 55 operations**, including the two local/aitesters-only outbox operations (53 operations without that profile). Reviewed their request and response models, the five exception-advice classes, authentication/rate-limit filters, and the service branches that determine documented statuses. Base source revision: `8cb264a24ef997d635210bc5d0152363f78f8486`; corrections are local working-tree changes on top of it.

This extends DOC-01–DOC-10 from the sibling `playwright-2026` bug register. It changes documentation and schema generation, not application validation, authorization, persistence, or response serialization.

Before adding regression checks, bounded exploration against `http://localhost:8081` confirmed JSON message-shaped 401 responses for cart, orders, inventory, MFA status, outbox and traffic info; empty forgot-password and MFA sign-in payloads produced field-keyed JSON 400 responses. These probes created no accounts, messages or MFA challenges. The running stack is not evidence of deployment of the edited checkout; source and in-process generated-spec tests establish the corrected contract.

## Corrections

- All declared error responses have explicit JSON content and error models instead of inferred success DTOs. Validation maps, domain message errors and upstream proxy errors remain distinct. `ErrorDto.message` is required.
- Added missing inventory 400/404/409 branches, cart/order stock conflicts, MFA 422 password rejection and missing-account branches, public password endpoint Bearer rejection, and traffic/outbox security errors.
- Made every void success response explicitly bodyless. Product DELETE and traffic lookup 404 responses are bodyless. Disabled springdoc's generic exception-response inference: it was adding unreachable product 404 responses and replacing explicitly empty bodies.
- Declared QR success as PNG binary and streaming successes as one DTO per SSE data event. Ollama errors use JSON before streaming begins; other upstream statuses have a documented default response. Once streaming starts, a failure may terminate the stream rather than change the HTTP status.
- Corrected nullable login/challenge/profile fields, prompt resets, optional product images, inventory order/request references, reset tokens, email failure details, local outbox template values, traffic metadata and optional streamed fields. Nullable references use an explicit null alternative, and nullable enum constraints include JSON null.
- Traffic request/response bodies now describe arbitrary JSON or text previews rather than Jackson's internal `JsonNode` accessors. Headers are JSON objects.
- Product, order and inventory `LocalDateTime` fields use `local-date-time`, explicitly without an offset. They are deployment-local wall-clock values, not RFC 3339 instants. Existing timestamp values are unchanged.
- Nonblank inputs retain a positive minimum and non-whitespace constraint; a final schema customizer preserves the minimum where `@Size(max=...)` otherwise replaces it with zero. Product prices document two fractional digits and eight integer digits. Inventory adjustments document nonzero delta and idempotency. Computed validation getters are read-only properties rather than client input.
- Documented pagination bounds/clamping, inventory thresholds, product partial-update semantics, password-reset token exposure configuration, asynchronous email delivery and the actual order-cancellation restriction.
- Traffic Bearer security and session-header requirements follow `app.traffic.legacy-public-access`. Local outbox requires Bearer authentication and administrator privileges; configured access-key enforcement adds an AND requirement for `X-Local-Outbox-Key`. Rate-limit schemas are checked both enabled and disabled.

## Controller coverage

| Controller | Operations | Reviewed areas |
|---|---:|---|
| AdminInventoryController | 4 | Pagination, thresholds, adjustment idempotency, 400/404/409, references, timestamps |
| CartController | 2 | Authentication errors, cart success, empty clear response |
| CartItemsController | 3 | Validation, missing items/products, stock conflicts |
| EmailController | 1 | Validation, authentication, rate limit, asynchronous bodyless success |
| LocalEmailOutboxController | 2 | Profile availability, administrator/key requirements, nullable template, empty clear |
| OllamaController | 4 | SSE chunks, JSON errors, upstream status forwarding, tool schemas |
| OrderController | 6 | Validation/domain errors, stock conflict, ownership, pagination, cancellation, timestamps |
| PasswordResetController | 2 | Validation/domain errors, Bearer rejection, nullable exposure-controlled token, empty reset |
| ProductController | 5 | Error models, nullable image, timestamps, create/update constraints, empty delete |
| QrController | 1 | PNG success, JSON validation/authentication/rate-limit errors |
| TrafficController | 3 | Configuration-dependent security, session header, filters, JSON/text bodies, empty 404 |
| UserDeleteController | 1 | Administrator requirement, message errors, empty 204 |
| UserEditController | 1 | Validation, ownership errors, entity serialization without password, nullable metadata |
| UserEmailEventController | 1 | Authentication errors, nullable failure detail |
| UserGetSingleUserController | 1 | Authentication and missing-user errors, public profile fields |
| UserGetUsersController | 1 | List response, authentication errors, nullable names |
| UserLogoutController | 1 | Authentication errors, refresh-token revocation, empty 200 |
| UserMeController | 1 | Profile response and authentication errors |
| UserMfaController | 6 | Validation, authentication vs password failure, conflict/expiry/missing-account branches, empty disable |
| UserPromptController | 4 | Effective GET vs stored PUT, reset semantics, nullable values, validation/authentication |
| UserRefreshController | 1 | Validation vs authentication error, token rotation, rate limit |
| UserRightToBeForgottenController | 1 | Ownership errors, deletion scope, empty 204 |
| UserSignInController | 1 | Validation/authentication errors, MFA alternatives and nulls, rate limit |
| UserSignUpController | 1 | Field/domain errors, Bearer rejection, empty 201, rate limit |
| UserSsoController | 1 | Validation map, provider errors, identity conflict, login response |

## Regression coverage

`OpenApiControllerAuditTest` inspects the generated `/v3/api-docs` rather than merely reflecting on annotations. Its local-profile subclass also covers the outbox. Checks include all documented error models/media types, every void controller return, PNG/SSE success schemas, nonblank DTO minima, local timestamp formats, decimal precision, nullable references/enums, traffic JSON bodies, inventory conflicts and resolution of every internal schema reference.

`OpenApiSecurityProfilesTest` and its legacy-profile subclass cover both traffic modes and outbox-key enforcement. `OpenApiRateLimitDocumentationTest` checks 429 error schemas with rate limiting enabled; the existing contract tests check disabled behavior and sorted status codes. Existing controller annotation-coverage tests cover every mapped operation in both profiles.

Generated inspection artifacts are written under `target/openapi-audit-OpenApiControllerAuditTest.json` and `target/openapi-audit-OpenApiControllerAuditLocalProfileTest.json`. They contain contracts, not issued credentials. Null-composition checks cover type/enum/reference/composition interactions; they are not a general-purpose JSON Schema validator.

## Verification and limits

Final verification on 2026-09-10:

- `./mvnw clean verify`: **BUILD SUCCESS; 458 tests, zero failures/errors/skips**, including 49 OpenAPI/Swagger checks. PMD, SpotBugs and JaCoCo coverage gates passed. Final build completed at 15:06:48 +02:00.
- `npm run test:api` in `playwright-2026`: **80 passed**. This suite exercises the existing gateway stack, not a deployment of these local edits.
- `git diff --check`: passed in both repositories.

This is a complete controller inventory and source/generated-contract audit of the listed cases, not a claim that every possible runtime payload or external provider failure was reproduced. Generic malformed-JSON/media-type/method handling and the separate functional BUG reports remain distinct from these documentation corrections. No real identity-provider session, live model streaming session or production rollout was performed. The original exploratory snapshots and deployment-retest statuses are preserved.

## Readability refactor

The customizer separates schema normalization, traffic security and outbox security into focused methods. The audit tests use stream traversal, named records and small scenarios with given/when/then markers only. `OpenApiDocument` centralizes contract lookup and null-composition checks. Existing assertions remain covered; the increased test count reflects splitting combined scenarios.
