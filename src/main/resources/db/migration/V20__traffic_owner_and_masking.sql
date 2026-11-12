-- Catalog X-03: traffic records are bound to the user who made them, and stored history is masked again.
--
-- owner_username: the authenticated user of a request (null for anonymous requests). Rows stored before
-- this migration have no owner and are readable only through a session bound to its user below.
-- Guarded like V15: a schema baselined at V1 (legacy databases) may not have traffic_log.
DO $migration$
BEGIN
    IF to_regclass('traffic_log') IS NOT NULL THEN
        ALTER TABLE traffic_log ADD COLUMN IF NOT EXISTS owner_username varchar(255);
    END IF;
END
$migration$;

-- The first authenticated user seen on a traffic session; anonymous records of the session are readable
-- only by this user.
CREATE TABLE IF NOT EXISTS traffic_session_owner (
    client_session_id varchar(128) PRIMARY KEY,
    username varchar(255) NOT NULL,
    bound_at timestamptz NOT NULL
);

-- Version of the masking rules already applied to the stored rows. TrafficHistoryMasker re-masks every
-- stored request and response with the current rules on the first start after this migration (and after
-- every later rules change), then records the version here.
CREATE TABLE IF NOT EXISTS traffic_masking_state (
    id smallint PRIMARY KEY CHECK (id = 1),
    rules_version integer NOT NULL
);
INSERT INTO traffic_masking_state (id, rules_version) VALUES (1, 0) ON CONFLICT (id) DO NOTHING;
