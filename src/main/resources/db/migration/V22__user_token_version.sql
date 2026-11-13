-- Catalog X-08: access tokens carry the account's token version ("ver" claim). Logout and password reset
-- increment it, which revokes every access token issued before. Existing tokens have no claim and count as
-- version 0, so they stay valid until the account's first logout or reset, or until they expire.
-- Guarded like V19-V21: a schema baselined at V1 (legacy databases) may not have app_user.
DO $migration$
BEGIN
    IF to_regclass('app_user') IS NOT NULL THEN
        ALTER TABLE app_user ADD COLUMN IF NOT EXISTS token_version integer NOT NULL DEFAULT 0;
    END IF;
END
$migration$;
