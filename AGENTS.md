# Verification Commands

- Fast local verify: `./mvnw -Pfast-verify verify`
- Default verify (CI default): `./mvnw verify`
- Full verify with integration tests: `./mvnw -Pintegration-tests verify`
- Tests only: `./mvnw test`

# Course API Contract

- Treat `https://awesome.byst.re` as the authoritative course API contract: preserve compatibility with its documented and live behavior, verify uncertain expectations against that environment, and keep every affected lesson suite green when backend behavior changes.
