DO $migration$
BEGIN
    IF to_regclass('password_reset_token') IS NOT NULL THEN
        ALTER TABLE password_reset_token
            ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

        ALTER TABLE password_reset_token
            ALTER COLUMN version DROP DEFAULT;

        CREATE INDEX IF NOT EXISTS idx_password_reset_token_user_id
            ON password_reset_token (user_id);
        CREATE INDEX IF NOT EXISTS idx_password_reset_token_expires_at
            ON password_reset_token (expires_at);
    END IF;
END
$migration$;
