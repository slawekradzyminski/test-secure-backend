-- Usernames and emails are unique regardless of letter case (catalog T-008). The application checks
-- this on every signup, profile edit and SSO login; these indexes add the database guarantee.
--
-- Accounts created before the rule may already collide (for example 'Abc' and 'abc'). Creating the
-- index would then fail and block the deployment, so each index is created only when no collision
-- exists; otherwise the migration logs a warning with the number of colliding groups and continues.
-- The colliding accounts keep working and are listed by GET /api/v1/admin/users/identity-issues.
-- After the clean-up, create the skipped index by hand with the statement from the warning.
DO $identity$
DECLARE
    collisions bigint;
BEGIN
    SELECT count(*) INTO collisions FROM (
        SELECT lower(username) FROM app_user GROUP BY lower(username) HAVING count(*) > 1
    ) duplicates;
    IF collisions = 0 THEN
        CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_username_lower ON app_user (lower(username));
    ELSE
        RAISE WARNING 'Skipped ux_app_user_username_lower: % usernames differ only in letter case. '
            'Resolve them, then run: CREATE UNIQUE INDEX ux_app_user_username_lower ON app_user (lower(username));',
            collisions;
    END IF;

    SELECT count(*) INTO collisions FROM (
        SELECT lower(email) FROM app_user GROUP BY lower(email) HAVING count(*) > 1
    ) duplicates;
    IF collisions = 0 THEN
        CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_email_lower ON app_user (lower(email));
    ELSE
        RAISE WARNING 'Skipped ux_app_user_email_lower: % emails differ only in letter case. '
            'Resolve them, then run: CREATE UNIQUE INDEX ux_app_user_email_lower ON app_user (lower(email));',
            collisions;
    END IF;
END
$identity$;
