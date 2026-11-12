-- Catalog X-08: access tokens carry the account's token version ("ver" claim). Logout and password reset
-- increment it, which revokes every access token issued before. Existing tokens have no claim and count as
-- version 0, so they stay valid until the account's first logout or reset, or until they expire.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS token_version integer NOT NULL DEFAULT 0;
