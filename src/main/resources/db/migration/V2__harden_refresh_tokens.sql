ALTER TABLE refresh_token
    ADD COLUMN token_hash varchar(64),
    ADD COLUMN family_id varchar(36),
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN consumed_at timestamptz,
    ADD COLUMN replaced_by_token_hash varchar(64);

UPDATE refresh_token
SET token_hash = encode(sha256(convert_to(token, 'UTF8')), 'hex'),
    family_id = gen_random_uuid()::text;

ALTER TABLE refresh_token
    ALTER COLUMN token_hash SET NOT NULL,
    ALTER COLUMN family_id SET NOT NULL,
    ALTER COLUMN created_at DROP DEFAULT;

ALTER TABLE refresh_token DROP CONSTRAINT IF EXISTS ukr4k4edos30bx9neoq81mdvwph;
ALTER TABLE refresh_token DROP CONSTRAINT IF EXISTS refresh_token_token_key;
ALTER TABLE refresh_token DROP COLUMN token;
ALTER TABLE refresh_token ADD CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash);

CREATE INDEX idx_refresh_token_family_id ON refresh_token (family_id);
