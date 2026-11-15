-- Broker transport (docs/EVENT_BROKER_CONTRACT.md in awesome-localstack): domain_events is
-- the transactional outbox of the event relay, which sends every row with published_at IS NULL
-- to Artemis and then sets published_at. The feed (GET /internal/v1/events) ignores the column.
--
-- Events recorded more than an hour before this migration count as published: consumers have
-- read them from the feed. The last hour is relayed once when the backend first runs with
-- EVENTS_TRANSPORT=broker, so a consumer switched from the feed in the same deployment cannot
-- miss an event it had not polled yet; consumers skip what they already handled by event id.
-- Legacy schemas are baselined at V1, hence the guard.
DO $migration$
BEGIN
    IF to_regclass('domain_events') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE domain_events ADD COLUMN IF NOT EXISTS published_at timestamptz;
    UPDATE domain_events SET published_at = recorded_at
    WHERE published_at IS NULL AND recorded_at < now() - interval '1 hour';
    CREATE INDEX IF NOT EXISTS idx_domain_events_unpublished ON domain_events(sequence)
        WHERE published_at IS NULL;
END
$migration$;
