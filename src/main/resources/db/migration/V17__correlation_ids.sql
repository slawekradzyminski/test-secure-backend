-- Correlation ids on persisted hand-offs (X-Request-Id and W3C trace context).
--
-- domain_events.request_id / trace_id: the X-Request-Id and trace id of the
-- request whose transaction wrote the event, served on the event feed as
-- requestId / traceId. Null for events written without a request (scheduled
-- jobs) and for every event recorded before this migration.
--
-- email_event.correlation_request_id / traceparent and webhook_deliveries.request_id /
-- traceparent: the ids of the request that queued the email or the delivery, so
-- the delayed send and the retry jobs log under them and webhook deliveries send
-- them on (X-Request-Id, child traceparent). email_event.request_id already holds
-- the idempotency key of warehouse-service notifications, hence the longer name.
ALTER TABLE domain_events ADD COLUMN IF NOT EXISTS request_id varchar(128);
ALTER TABLE domain_events ADD COLUMN IF NOT EXISTS trace_id varchar(32);

DO $migration$
BEGIN
    IF to_regclass('email_event') IS NOT NULL THEN
        ALTER TABLE email_event ADD COLUMN IF NOT EXISTS correlation_request_id varchar(128);
        ALTER TABLE email_event ADD COLUMN IF NOT EXISTS traceparent varchar(55);
    END IF;

    IF to_regclass('webhook_deliveries') IS NOT NULL THEN
        ALTER TABLE webhook_deliveries ADD COLUMN IF NOT EXISTS request_id varchar(128);
        ALTER TABLE webhook_deliveries ADD COLUMN IF NOT EXISTS traceparent varchar(55);
    END IF;
END
$migration$;
