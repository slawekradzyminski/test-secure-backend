# Distributed tracing

The backend can export real OpenTelemetry spans (Micrometer Tracing with the
OpenTelemetry bridge, OTLP over HTTP with the JDK `HttpClient` sender; the same
dependencies and switches as warehouse-service: `spring-boot-micrometer-tracing-opentelemetry`,
`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp` without OkHttp,
`opentelemetry-exporter-sender-jdk`, no OTLP metrics registry). Tracing is **off by default**; the
correlation ids (`X-Request-Id`, `requestId` / `traceId` / `spanId` in the MDC,
see the README) work the same either way.

## Configuration

| Environment variable | Property | Default | Meaning |
| --- | --- | --- | --- |
| `TRACING_ENABLED` | `app.tracing.enabled` | `false` | Switches sampling, W3C propagation, export and the server-span ids in the MDC |
| `TRACING_OTLP_ENDPOINT` | `management.opentelemetry.tracing.export.otlp.endpoint` | `http://jaeger:4318/v1/traces` | OTLP/HTTP traces endpoint (Jaeger, an OpenTelemetry Collector) |
| `TRACING_SAMPLING_PROBABILITY` | `management.tracing.sampling.probability` | `1.0` | Probability for root spans, used only when enabled |

The service name is `backend` (`management.opentelemetry.resource-attributes.service.name`).
It is not set through `spring.application.name`, because that would add a
`service.name` field to the ECS JSON logs even with tracing off. Propagation is W3C
`traceparent` only (`management.tracing.propagation.type=w3c`, no baggage). The other
`management.opentelemetry.tracing.export.*` properties (timeouts, headers, compression,
batch sizes) apply as documented by Spring Boot.

## One bean graph for the native image

The GraalVM native image fixes which beans exist at build time, so tracing is not
switched with conditions or profiles. The tracer, the span processor and the
instrumentation always exist; `TracingConfiguration` replaces four of Spring Boot's
beans and decides at runtime:

| Bean | Tracing off | Tracing on |
| --- | --- | --- |
| `Sampler` | always off, whatever the parent says | parent-based; roots with the configured probability, except `INTERNAL` roots |
| `ContextPropagators` | no-op: no `traceparent` is read or injected by instrumentation | W3C `traceparent` |
| `SpanExporter` | no-op, no HTTP client is created | OTLP/HTTP (JDK `HttpClient`) |
| Micrometer MDC listener | silent | silent: the correlation code writes the MDC from the server or work span |

Spring Boot's own OTLP exporter is disabled (`management.tracing.export.otlp.enabled=false`)
because it creates its JDK `HttpClient` (and its selector thread) eagerly, even with
tracing off. With tracing off, observations still
create non-recording spans (cheap: no attributes, no export, no MDC writes) and
headers, MDC entries and log lines are byte-for-byte those of the correlation code
alone (`TracingDisabledTest`).

`INTERNAL` root spans are the ticks of `@Scheduled` jobs (traffic flush every 100 ms,
traffic publisher every 500 ms, retry polls, retention). They are never sampled, so
idle polling does not fill the trace store; work a job picks up runs in its own span,
see Outboxes. (warehouse-service lists its two poll methods by name; the backend has
six jobs and no scheduled work that should start a trace, so the rule is generic.)

## What a trace contains

- **Server span.** `ServerHttpObservationFilter` starts the `http <method> <route>`
  span (`HIGHEST_PRECEDENCE + 1`) as the child of the incoming `traceparent` (the
  gateway always sends one). `ServerSpanCorrelationFilter` (`+ 2`) then puts that
  span's trace and span id into the MDC, so the logged `traceId`/`spanId` and the
  stored `domain_events.trace_id`/`span_id` are the server span's. `CorrelationFilter`
  stays first (unlike warehouse-service, where it moved after the observation
  filter): Logbook's secure filter is also at `HIGHEST_PRECEDENCE + 1` and must keep
  logging under the request's ids with tracing off.
- **Ollama calls.** The Ollama `WebClient` is instrumented while tracing is on: its
  `http post` client span is a child of the request's span and is the only
  `traceparent` sent; `CorrelationExchangeFilter` then adds `X-Request-Id` only.
  With tracing off the client is not instrumented and the filter sends the
  hand-written child `traceparent` as before.
- **Event feed.** `domain_events.span_id` (V24) records the MDC `spanId` next to
  `trace_id`; the feed serves it as `spanId` after `traceId`. Consumers start their
  `process <type>` span as a child of `traceId`/`spanId`. With tracing off `spanId`
  is the hand-written span id of the writing hop (not an exported span).
- **Outboxes.** Email events and webhook deliveries store the `traceparent` of the
  queuing span with its real sampled flag. The delayed email send, `EmailRetryJob`
  and `WebhookDispatcher` run each unit of work in a `PRODUCER` span (`send email`,
  `deliver webhook`) parented by that stored `traceparent`; a row without a
  valid stored `traceparent` starts a new trace. Webhook deliveries send that span
  as their `traceparent`. JMS sends inside the email span are its children.
- **Not traced:** JDBC statements and Hikari pool waits (Spring Boot 4.1 has no
  built-in JDBC observation; the third-party datasource proxy was left out to keep
  the native image unchanged).

## Known limits

- Logs written before the server span starts (Logbook's secure filter, traffic
  capture) carry the incoming trace id and a provisional span id, or a provisional
  trace id when the request has no `traceparent`.
- Logs inside child spans (Spring Security, the Ollama client span) carry the server
  span id, not the child's.

## Trying it

```bash
docker run -d --name jaeger -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:1.76.0
TRACING_ENABLED=true TRACING_OTLP_ENDPOINT=http://localhost:4318/v1/traces ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Then open `http://localhost:16686` and look for the `backend` service.
