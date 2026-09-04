# Verification Commands

- Fast local verify: `./mvnw -Pfast-verify verify`
- Default verify (CI default): `./mvnw verify`
- Full verify with integration tests: `./mvnw -Pintegration-tests verify`
- Tests only: `./mvnw test`
- Targeted mutation feedback: `./mvnw -Pmutation-testing test-compile pitest:mutationCoverage`

# Mutation-testing workflow

- Run the normal tests first. Use the mutation profile after changing the selected authentication, cart, order, product, traffic, or LLM-handler code, or after changing their tests.
- Treat `NO_COVERAGE` as a reachability gap and `SURVIVED` as an assertion gap. For each meaningful survivor, add a test that passes on the original code and fails on that mutant.
- Do not optimize for the headline score by asserting implementation details or snapshotting entire objects. Classify equivalent and inconsequential mutants explicitly in the handoff.
- Keep this targeted run advisory while the baseline is being established. In CI, reject new meaningful survivors in security, money, authorization, sanitization, and state-transition logic before introducing a repository-wide percentage gate.

# Course API Contract

- Use the tests from the [AI Testers API course repository](https://github.com/AI-Testers-pl/ait2api1-api-ai) as the compatibility feedback loop for `https://awesome.byst.re`.
- Run the latest available lesson suite as part of whole-stack verification in `awesome-localstack`, where the backend and its dependencies are exercised together.
- Local backend changes that are not being released to production require the Maven checks above, not external course-suite runs against deployed environments. Do not run those external checks solely for a backend audit or cleanup.
- The lesson tests define the required course contract. Endpoints and behavior not covered by that suite may change; covered behavior must remain compatible.
