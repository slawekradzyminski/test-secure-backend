-- Catalog X-04: wrong codes per 2FA sign-in challenge. The challenge is consumed after
-- app.mfa.max-challenge-attempts (default 5) wrong codes, independent of rate limiting.
ALTER TABLE mfa_challenge ADD COLUMN IF NOT EXISTS failed_attempts integer NOT NULL DEFAULT 0;
