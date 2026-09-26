# Low-level test and dependency audit — 2026-09-26

## Findings and changes

- There is no `RestTemplate` in the repository. `HttpHelper` uses the synchronous `RestClient`; the Ollama services already use `WebClient`. The synchronous helper remains on `RestClient`.
- A Dependabot Actions check failed when `CreateQrControllerTest` could not decode a randomly generated QR payload. `QrServiceTest` already verifies decoding. The controller test now uses fixed input and checks that the HTTP response contains a readable PNG, without repeating the service assertion.
- `ChatMessageDtoTest` constructed a `ValidatorFactory` for every test and never closed it. The class now shares one factory and closes it after its tests.
- Three low-value test classes were removed after review: `TrafficConfigTest` asserted ordinary JDK queue behavior without testing Spring wiring; `ProductExceptionHandlerTest` and `UserExceptionHandlerTest` called one-line message mappers directly while endpoint tests already verify the 404 status and response body through Spring MVC.
- The positive MFA encryption test now checks an encrypt/decrypt round trip instead of merely asserting that bean creation does not throw.
- The `integration-tests` profile reported success while Failsafe ran zero tests. Its includes now use `.java` patterns and `failIfNoTests` is enabled. The profile executes 41 tests.
- PIT omitted `ProductPaginationTest` and the real product tool registry from its selected test list. Including them removed false survivors in product filtering and tool registration.
- Added focused assertions for missing products and orders, inventory threshold validation, IP and identity rate limits, gRPC rejection listeners, numeric product IDs, malformed password-reset URLs, and sanitized tool errors. These cover observable error and authorization behavior.
- Removed the unused `spring-boot-starter-restclient-test` dependency. Maven dependencies and test plugins were updated to the latest stable versions available during this audit. Dependabot's Maven PR limit increased from one to five so development updates can appear alongside the production group.

## Verification

| Check | Result |
| --- | --- |
| Baseline `./mvnw -Pfast-verify verify` | 523 tests passed |
| Final `./mvnw clean verify` | 537 tests passed; PMD and JaCoCo passed |
| Final `./mvnw -Pintegration-tests verify` | 537 default tests and 41 integration tests passed |
| Final `./mvnw -Pmutation-testing test-compile pitest:mutationCoverage` | 498 mutants; 487 killed, 8 timed out, 1 survived, 2 without coverage (99% PIT score) |
| Maven Versions check | No newer directly declared dependencies; remaining compiler updates require Maven 4 beta, while this project requires Maven 3.9 |

The removed tests and strengthened MFA assertion are outside PIT's selected tests, so the 99% mutation result remains applicable. The course API suite was not run because these changes are local and are not being released.

## Remaining PIT results

- `InventoryService.initial` returning `null` survives. Its only caller ignores the return value; the stock movement save is still asserted. This mutant has no observable effect in the current call path.
- Two `NO_COVERAGE` mutants replace returns from the emergency JSON-serialization fallbacks in `ProductCatalogFunctionHandler.errorMessage` and `ProductSnapshotFunctionHandler.buildErrorMessage`. Those branches require the configured Jackson mapper to fail while serializing a map containing one string; they are not reached with the application's mapper. The normal invalid-input and unexpected-error paths are covered.

## Dependabot review

- Maven PR [#62](https://github.com/slawekradzyminski/test-secure-backend/pull/62) proposes Spring Boot, Logbook, and springdoc updates. Its checks passed; those versions are included locally along with other stable Maven updates.
- Actions PR [#58](https://github.com/slawekradzyminski/test-secure-backend/pull/58) has a failed unit job caused by the QR test described above; its other checks passed. The workflow version changes remain in that PR.
- Docker PR [#50](https://github.com/slawekradzyminski/test-secure-backend/pull/50) has passing checks. Its image digest changes remain in that PR.
