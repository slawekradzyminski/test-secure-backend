-- Traffic bodies are bounded text, not large binary objects. Convert before unlinking
-- their old objects so migration failure rolls the entire change back.
DO $migration$
DECLARE
    payload_column text;
    old_object oid;
BEGIN
    IF to_regclass('traffic_log') IS NOT NULL THEN
        CREATE TEMP TABLE traffic_legacy_lobs (object_id oid PRIMARY KEY) ON COMMIT DROP;
        FOREACH payload_column IN ARRAY ARRAY['request_body', 'request_headers', 'response_body', 'response_headers'] LOOP
            IF EXISTS (
                SELECT 1 FROM pg_attribute
                WHERE attrelid = 'traffic_log'::regclass AND attname = payload_column
                  AND atttypid = 'oid'::regtype AND NOT attisdropped
            ) THEN
                EXECUTE format(
                    'INSERT INTO traffic_legacy_lobs SELECT DISTINCT %1$I FROM traffic_log WHERE %1$I IS NOT NULL ON CONFLICT DO NOTHING',
                    payload_column);
                EXECUTE format(
                    'ALTER TABLE traffic_log ALTER COLUMN %1$I TYPE text USING convert_from(lo_get(%1$I), ''UTF8'')',
                    payload_column);
            END IF;
        END LOOP;
        FOR old_object IN SELECT object_id FROM traffic_legacy_lobs LOOP
            PERFORM lo_unlink(old_object);
        END LOOP;
    END IF;
END
$migration$;
