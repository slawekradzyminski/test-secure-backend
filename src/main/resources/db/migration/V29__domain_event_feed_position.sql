-- Commit-ordered event feed (exploratory chaos session, finding F1). domain_events.sequence is
-- allocated when a row is inserted, so a transaction that inserts an event and commits late is
-- overtaken by a later one; a consumer that polled in between moved its cursor past the late
-- event and never saw it. The old settle delay (serve rows recorded 3 s ago) only narrowed that
-- window.
--
-- The feed now pages by feed_position, which is assigned only to committed rows, in commit
-- order, by one assigner at a time: the backend locks the single domain_event_feed_head row,
-- gives every committed row without a position max(last_position + 1, sequence), and stores the
-- last one. A row that commits late gets a position above everything already served. Existing
-- rows keep position = sequence, so consumer cursors stay valid.
DO $migration$
BEGIN
    IF to_regclass('domain_events') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE domain_events ADD COLUMN IF NOT EXISTS feed_position bigint;
    UPDATE domain_events SET feed_position = sequence WHERE feed_position IS NULL;
    CREATE UNIQUE INDEX IF NOT EXISTS uk_domain_events_feed_position ON domain_events(feed_position);
    CREATE INDEX IF NOT EXISTS idx_domain_events_unpositioned ON domain_events(sequence)
        WHERE feed_position IS NULL;

    CREATE TABLE IF NOT EXISTS domain_event_feed_head (
        id integer PRIMARY KEY CHECK (id = 1),
        last_position bigint NOT NULL
    );
    INSERT INTO domain_event_feed_head (id, last_position)
    SELECT 1, COALESCE(MAX(feed_position), 0) FROM domain_events
    ON CONFLICT (id) DO NOTHING;
END
$migration$;
