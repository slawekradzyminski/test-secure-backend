# GraalVM native image — spike and rollout, 8 November 2026

Branch `spike/native-image`. Goal: decide whether the backend can run as a GraalVM
native image on the single-vCPU, 4 GiB production VPS, using the unchanged AI Testers
course suite (`ai-testers-api`, lesson `l20`) as the acceptance gate.

## Status

Implemented on `feature/native-backend`: the bean graph no longer depends on the active
profile (GraphQL and gRPC always wired, `app.email.transport` selects Artemis or the local
outbox at runtime, bootstrap and seed runners check their properties at runtime, Flyway and
every actuator endpoint enabled in the base configuration). One native image now serves
`docker,server`, `aitesters` and `local`, passes `l20` 311/311 with both compatibility
backends on the same image, and peaked at 164–184 MiB during that run. `Dockerfile` builds
the native image (JVM fallback: `Dockerfile.jvm`); releases build it per architecture and run
`scripts/native-smoke.sh`.

## Spike verdict

**Go, after one targeted refactor.** The native backend passes `l20` (310/311; the one
failure is the profile limitation below), and the production profile set works end to end
including JMS to Artemis, gRPC, GraphQL, REST and QR codes. It starts in about 1.2 s and
runs in about 140–155 MiB instead of 360–410 MiB.

The blocker is not native support but how the backend selects beans: Spring AOT fixes
`@Profile` and `@ConditionalOn…` decisions, and keeps the build-time profiles active, when
the image is built. Today one JAR serves `docker,server`, `aitesters` and `local` by
switching profiles at runtime; a native image cannot.

## Measurements (arm64, local Docker)

| | JVM (production, 3.12.0) | Native (spike) |
| --- | --- | --- |
| Startup | tens of seconds on the VPS, plus C1 warm-up | 1.1–1.3 s, no warm-up |
| Memory after start | backend 409 MiB, aitesters-backend 363 MiB (production) | 135–155 MiB |
| `l20` (311 tests) | 311/311 | 310/311 (profile limitation) |
| Native build | – | 1.5–2 min, peak builder RSS 12.6–12.8 GB |
| Executable / image | – | 305–315 MB / about 550 MB uncompressed |

Across both backends the expected saving is roughly 450–500 MiB of RAM, and no JIT
compilation CPU after deploys and the nightly reset (the main CPU spikes on the VPS).

## What had to change

Kept on this branch, useful even without native:

- `artemis-server` and `artemis-jms-server` are test-scoped (only tests use the embedded broker).
- QR codes are written as 1-bit PNGs straight from ZXing's bit matrix, so the runtime no
  longer needs AWT/ImageIO; `zxing-javase` is test-scoped. The course's PNG checks pass.
- `JwtTokenFilter` logs unexpected exceptions instead of returning a silent 500.

Native-specific:

- `native` Maven profile: Spring AOT for an explicit profile set (`native.aot.profiles`),
  Hibernate build-time enhancement (lazy loading without runtime proxies; all 774 unit
  and 52 integration tests pass with enhanced classes on the JVM).
- `Dockerfile.native`: GraalVM 25 builder with the blocklist, Debian slim runtime with a
  bash `/dev/tcp` health check, `-Xmx384m`.
- `NativeRuntimeHints`: the `graphql-preview` schema and seed catalogue resources; Jackson
  binding for the DTO and seed packages (used outside controllers); Caffeine's generated
  cache classes; the `@PreAuthorize` SpEL targets; Artemis connection factories and
  generated `*_impl` bundles.
- `META-INF/native-image/com.awesome/backend-jms`: 59 reflection entries and the Artemis
  and Netty resources, recorded with the GraalVM tracing agent on the JVM against a real
  broker and filtered to JMS types.

## Findings

1. **Frozen bean graph.** GraphQL (10 beans), gRPC (3), JMS versus the local email outbox
   (3) and the property-gated bootstrap admin, bootstrap products and demo seed runners are
   decided at build time. On the production profile set the bootstrap admin was silently
   absent because `app.bootstrap-admin.enabled` was false during the build, and gRPC
   reflection could not be enabled at runtime.
2. **Build-time profiles stay active.** An image built for `local,server` still applies
   `application-server.yml` when started with `local` only; the compatibility stack's admin
   backend therefore issued UUID refresh tokens (the single `l20` failure).
3. **Reflection outside Spring's view** caused every other failure: SpEL in security
   annotations, Jackson outside controllers, Caffeine and Artemis loading classes by name.
   Each surfaced as a clear startup or request error; none was silent once
   `JwtTokenFilter` logged.

## To productionise

1. Make the bean graph profile-independent: always include GraphQL and gRPC (both public
   hosts already do), choose JMS versus local outbox and the bootstrap/seed runners by
   checking properties at runtime, move the server-only property values into the Compose
   environment. Then one image serves every profile, and runtime profiles only carry
   properties.
2. Keep the course gate: run `l20` against the native image in CI before every release,
   plus the JMS/gRPC smoke from this spike.
3. Publish per-architecture native builds as for the consumer and warehouse-service
   (GitHub's 16 GB runners fit the 12.8 GB peak).
4. Switch Compose health checks from `curl` to the image's bash check, drop the backend
   blocklist mounts (the blocklist is baked in at build time) and the JVM options.
