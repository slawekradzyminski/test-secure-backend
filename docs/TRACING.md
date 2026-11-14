# Distributed tracing

The backend can export real OpenTelemetry spans (Micrometer Tracing with the
OpenTelemetry bridge, OTLP over HTTP). Tracing is **off by default**; the
correlation ids (`X-Request-Id`, `requestId` / `traceId` / `spanId` in the MDC,
see the README) work the same either way.

## Configuration

| Environment variable | Property | Default | Meaning |
| --- | --- | --- | --- |
| `TRACING_ENABLED` | `app.tracing.enabled` | `false` | Switches sampling, W3C propagation, export and the tracer's MDC entries |
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
| MDC listener | inactive: the MDC belongs to `CorrelationFilter` / `CorrelationIds` | trace and span id of the span in scope |

Spring Boot's own OTLP exporter is disabled (`management.tracing.export.otlp.enabled=false`)
because it creates its HTTP client eagerly. With tracing off, observations still
create non-recording spans (cheap: no attributes, no export, no MDC writes) and
headers, MDC entries and log lines are byte-for-byte those of the correlation code
alone (`TracingDisabledTest`).

`INTERNAL` root spans are the ticks of `@Scheduled` jobs (the traffic buffer flushes
every 100 ms). They are never sampled, so idle polling does not fill the trace store;
work a job picks up runs in its own span, see Outboxes.

## What a trace contains

- **Server span.** `ServerHttpObservationFilter` starts the `http <method> <route>`
  span as the child of the incoming `traceparent` (the gateway always sends one), so
  its trace id is the logged `traceId` and the stored `domain_events.trace_id`. Inside
  it the MDC `traceId` and `spanId` are the tracer's current span (the server span or
  a child such as a Spring Security observation). `CorrelationFilter` runs before
  that filter and keeps `X-Request-Id` and `requestId` as before.
- **Ollama calls.** The Ollama `WebClient` is instrumented while tracing is on: its
  `http post` client span is a child of the request's span and is the only
  `traceparent` sent; `CorrelationExchangeFilter` then adds `X-Request-Id` only.
  With tracing off the client is not instrumented and the filter sends the
  hand-written child `traceparent` as before.
- **Event feed.** `domain_events.span_id` (V24) records the MDC `spanId` next to
  `trace_id`; the feed serves it as `spanId` after `traceId`. Consumers start their
  `process <type>` span as a child of `traceId`/`spanId`. With tracing off `spanId`
  is the hand-written span id of the writing hop (not an exported span).
- **Outboxes.** The delayed email send, `EmailRetryJob` and `WebhookDispatcher`
  run each unit of work in a `PRODUCER` span (`send email`, `deliver webhook`)
  parented by the `traceparent` stored when the work was queued; a row without a
  valid stored `traceparent` starts a new trace. Webhook deliveries send that span
  as their `traceparent`. JMS sends inside the email span are its children.
- **Not traced:** JDBC statements and Hikari pool waits (Spring Boot 4.1 has no
  built-in JDBC observation; the third-party datasource proxy was left out to keep
  the native image unchanged).

## Known limits

- The outbox rows store `traceparent` with the sampled flag `01`. With a sampling
  probability below 1, the dispatch span of an unsampled request is still sampled
  and appears as a trace whose parent span is missing.
- Logs written before the server span starts (between `CorrelationFilter` and the
  observation filter) carry the incoming trace id, or a provisional one when the
  request has no `traceparent`.

## Trying it

```bash
docker run -d --name jaeger -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:1.76.0
TRACING_ENABLED=true TRACING_OTLP_ENDPOINT=http://localhost:4318/v1/traces ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Then open `http://localhost:16686` and look for the `backend` service.
