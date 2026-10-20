-- PostgreSQL text cannot contain NUL. Make legacy binary preview markers visible
-- before immutable V6 converts OID payloads. No-op once those columns are text.
DO $callback$
DECLARE
    payload_column text;
    payload_object oid;
    payload bytea;
    null_position integer;
BEGIN
    IF to_regclass('traffic_log') IS NULL THEN
        RETURN;
    END IF;
    FOREACH payload_column IN ARRAY ARRAY['request_body', 'request_headers', 'response_body', 'response_headers'] LOOP
        IF EXISTS (
            SELECT 1 FROM pg_attribute
            WHERE attrelid = 'traffic_log'::regclass AND attname = payload_column
              AND atttypid = 'oid'::regtype AND NOT attisdropped
        ) THEN
            FOR payload_object IN EXECUTE format(
                'SELECT DISTINCT %1$I FROM traffic_log WHERE %1$I IS NOT NULL', payload_column)
            LOOP
                payload := lo_get(payload_object);
                null_position := position(decode('00', 'hex') IN payload);
                IF null_position > 0 THEN
                    WHILE null_position > 0 LOOP
                        payload := substring(payload FROM 1 FOR null_position - 1)
                            || convert_to(chr(9216), 'UTF8')
                            || substring(payload FROM null_position + 1);
                        null_position := position(decode('00', 'hex') IN payload);
                    END LOOP;
                    PERFORM lo_put(payload_object, 0, payload);
                END IF;
            END LOOP;
        END IF;
    END LOOP;
END
$callback$;
