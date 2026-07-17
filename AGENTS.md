# Verification Commands

- Fast local verify: `./mvnw -Pfast-verify verify`
- Default verify (CI default): `./mvnw verify`
- Full verify with integration tests: `./mvnw -Pintegration-tests verify`
- Tests only: `./mvnw test`

# Course API Contract

- Use the tests from the [AI Testers API course repository](https://github.com/AI-Testers-pl/ait2api1-api-ai) as the compatibility feedback loop for `https://awesome.byst.re`.
- After awesome-localstack deployments, run the suite from the [latest available lesson (`l12` currently)](https://github.com/AI-Testers-pl/ait2api1-api-ai/tree/master/l12) and keep it green (`cd l12 && npm ci && npm test`).
- The lesson tests define the required course contract. Endpoints and behavior not covered by that suite may change; covered behavior must remain compatible.
