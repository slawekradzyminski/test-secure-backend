-- Distributed tracing (docs/TRACING.md): domain_events.span_id is the span id of the
-- writing request (the MDC spanId: the tracer's current span with tracing on, the
-- hand-written hop span id with tracing off), served on the event feed as spanId after
-- traceId. Consumers start their "process <type>" span as a child of traceId/spanId, so
-- a cancellation and the refund it triggers are one trace. Null like trace_id for events
-- written without a request and for every event recorded before this migration.
ALTER TABLE domain_events ADD COLUMN IF NOT EXISTS span_id varchar(16);
