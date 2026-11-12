-- Catalog X-04: wrong codes per 2FA sign-in challenge. The challenge is consumed after
-- app.mfa.max-challenge-attempts (default 5) wrong codes, independent of rate limiting.
-- Guarded like V15: a schema baselined at V1 (legacy databases) may not have mfa_challenge.
DO $migration$
BEGIN
    IF to_regclass('mfa_challenge') IS NOT NULL THEN
        ALTER TABLE mfa_challenge ADD COLUMN IF NOT EXISTS failed_attempts integer NOT NULL DEFAULT 0;
    END IF;
END
$migration$;
