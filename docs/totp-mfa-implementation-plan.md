# TOTP multi-factor authentication implementation plan

## Goal

Add opt-in, application-owned TOTP multi-factor authentication for local password accounts. The implementation must work with Microsoft Authenticator's standard "Other account" TOTP mode and remain compatible with other RFC 6238 authenticator apps.

Production does not run Keycloak and explicitly disables SSO, so the production MFA ceremony belongs to the Spring backend. SSO accounts remain the responsibility of their identity provider and cannot enroll in application-owned TOTP.

## Technology choices

- Keep the existing stateless JWT and refresh-token architecture.
- Continue using Spring Security's `AuthenticationManager` for the password factor.
- Use `com.github.bastiaanjansen:otp-java` for RFC 6238 secret generation, OTPAuth URI generation, and TOTP calculation.
- Use Spring Security Crypto's authenticated AES-256-GCM encryptor for stored TOTP secrets.
- Use the configured Spring `PasswordEncoder` for recovery-code verifier hashes.
- Do not enable Spring Security's session-oriented `@EnableMultiFactorAuthentication`: its factor authorities and redirect flow do not fit the current REST endpoint that issues an application JWT only after login completes.

## API contract

### Enrollment and account management

- `GET /api/v1/users/2fa/status` returns whether TOTP is enabled and how many unused recovery codes remain.
- `POST /api/v1/users/2fa/setup` requires an application JWT, creates a pending secret, and returns the manual secret, OTPAuth URI, and QR-code data URI. A new setup replaces only an unconfirmed setup.
- `POST /api/v1/users/2fa/confirm` requires an application JWT plus a valid TOTP code. It enables MFA and returns recovery codes exactly once.
- `POST /api/v1/users/2fa/recovery-codes` requires the current password plus a valid TOTP code and replaces all previous recovery codes.
- `POST /api/v1/users/2fa/disable` requires the current password plus a valid TOTP or recovery code, deletes the MFA credential and recovery codes, and revokes all refresh tokens.

### Sign-in

- Existing accounts without MFA continue receiving the current login response.
- After a correct password for an MFA-enabled account, `POST /api/v1/users/signin` returns `mfaRequired=true` and a short-lived opaque challenge token, but no access or refresh token.
- `POST /api/v1/users/signin/2fa` accepts the challenge token and either a TOTP or recovery code. It consumes both the challenge and accepted factor, then returns the normal JWT and refresh token.

## Persistence and security invariants

- Store MFA credentials, login challenges, and recovery codes in dedicated tables.
- Encrypt TOTP secrets with a deployment-specific password and salt kept outside the database.
- Store only a SHA-256 digest of each random login challenge.
- Store recovery-code selectors separately and hash their verifier portion with Spring's password encoder, so validation performs one bounded hash comparison.
- Challenges are random, single-use, expire after five minutes, and are invalidated when a newer password login creates another challenge.
- Accept the current TOTP time step and one adjacent step in either direction for clock skew.
- Record the last accepted time step and reject replay of that step.
- Use pessimistic database locking while consuming a challenge, TOTP step, or recovery code.
- Rate-limit MFA completion by client IP and challenge identity. Rate-limit authenticated enrollment/management by username.
- Never log secrets, OTPAuth URIs, QR payloads, TOTP values, challenge tokens, or recovery codes. Add those field names to request-log redaction.
- Password reset does not disable or bypass MFA. Disabling MFA and regenerating recovery codes require the current password and a second factor.
- Account deletion removes all MFA state.

## Frontend

- Model sign-in as a two-stage flow and store no application tokens until MFA completes.
- Add an MFA code/recovery-code form after a challenge response.
- Add a security section to the authenticated profile for status, enrollment, confirmation, recovery-code display/regeneration, and disabling.
- Display recovery codes once with copy/download affordances and an explicit acknowledgement.
- Keep QR generation server-side so the browser receives only a display-ready image and manual setup key.

## Verification and rollout

1. Backend unit tests cover encryption, time-window validation, replay prevention, challenges, recovery codes, and service policy.
2. Backend HTTP tests cover the complete enrollment and login lifecycle, invalid/expired/replayed factors, authorization, rate limiting, password-reset behavior, account deletion, and OpenAPI contracts.
3. Frontend unit tests cover both login response branches and MFA management states.
4. Playwright covers enrollment with a programmatically generated current TOTP, MFA login, recovery-code login, regeneration, and disable.
5. Run backend `./mvnw verify`, frontend lint/typecheck/unit/build, and the Dockerized e2e stack.
6. Add production secrets to Ansible Vault, deploy versioned backend/frontend images, run deployment verification, and smoke-test live MFA using a disposable production test user.
7. Hand over for a manual Microsoft Authenticator e2e only after automated local and live verification pass.
