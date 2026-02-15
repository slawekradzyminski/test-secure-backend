# Test Improvement Plan

## Baseline

- Command: `./mvnw verify`
- Result: `120.72s` real time
- Build phase split:
  - `./mvnw test`: `101.35s`
  - `./mvnw -DskipTests verify`: `8.57s`
- Main observation: test execution dominates runtime.

## Optimization Steps

1. Lower BCrypt cost for test profiles only.
2. Remove unnecessary real websocket handshake from `WebSocketIntegrationTest`.
3. Reuse auth fixture tokens in the heaviest Ollama endpoint test classes.
4. Disable SQL logging in JPA slice tests.
5. Add a local fast-verify Maven profile that keeps default CI behavior unchanged.
6. Refactor `TrafficConfigTest` to a lightweight unit-level config test.
7. Increase Spring test context cache and reduce context churn.
8. Enable class-level JUnit 5 parallel execution in Surefire.
9. Add Mockito Java agent to Surefire/Failsafe to avoid runtime self-attach.
10. Split heavy integration classes out of default Surefire and run them in `integration-tests` profile via Failsafe.
11. Upgrade Maven wrapper distribution.

## Verification Log

| Step | Change | Command | Real Time | Delta vs Baseline |
| --- | --- | --- | --- | --- |
| 0 | Baseline | `./mvnw verify` | `120.72s` | `0.00s` |
| 1 | Test-only BCrypt strength override (`12 -> 4`) | `./mvnw -q test` | `54.03s` | `-66.69s` vs baseline `verify` / `-47.32s` vs baseline `test` |
| 2 | `WebSocketIntegrationTest` converted to lightweight unit test | `./mvnw -q test` | `57.01s` (noisy run) | class-level: `WebSocketIntegrationTest 15.959s -> 0.280s` |
| 3 | Reuse auth token in Ollama endpoint suites (`@BeforeAll`) | `./mvnw -q test` | included in final run | class-level: `OllamaChatControllerTest 9.893s -> 3.792s`, `OllamaGenerateControllerTest 3.407s -> 0.178s` |
| 4 | Disable SQL/springdoc overhead in test profile and JPA slices | `./mvnw -q test` | `64.45s` (conservative measurement) | stable class sum from surefire: `104.97s -> 35.65s` |
| 5 | Add opt-in Maven `fast-verify` profile | `./mvnw -q verify` | `65.90s` | `-54.82s` |
| 5b | Fast profile (`-Pfast-verify`) | `./mvnw -q -Pfast-verify verify` | `43.05s` | `-77.67s` |
| 6-11 | Phase 2 cumulative (remaining suggestions implemented) | `./mvnw -q verify` | `53.99s` | `-66.73s` |
| 6-11 | With integration profile | `./mvnw -q -Pintegration-tests verify` | `74.80s` | `-45.92s` |
| 6-11 | With fast profile | `./mvnw -q -Pfast-verify verify` | `36.30s` | `-84.42s` |

## Implemented Changes

1. Added test-only security config with lower BCrypt cost:
   - `src/test/java/com/awesome/testing/config/TestSecurityConfig.java`
2. Replaced `WebSocketIntegrationTest` Spring+network setup with direct `TrafficPublisher` test:
   - `src/test/java/com/awesome/testing/traffic/WebSocketIntegrationTest.java`
3. Reused auth token per class in heavy Ollama controller tests:
   - `src/test/java/com/awesome/testing/endpoints/ollama/OllamaChatControllerTest.java`
   - `src/test/java/com/awesome/testing/endpoints/ollama/OllamaGenerateControllerTest.java`
4. Reduced test profile overhead:
   - `src/test/resources/application-test.yml`
   - `src/test/java/com/awesome/testing/repository/OrderRepositoryTest.java`
   - `src/test/java/com/awesome/testing/repository/CartItemRepositoryTest.java`
5. Added opt-in fast local verify profile:
   - `pom.xml`
6. Removed obsolete websocket test config:
   - deleted `src/test/java/com/awesome/testing/config/WebSocketTestConfig.java`
7. Refactored `TrafficConfigTest` to avoid full Spring Boot startup:
   - `src/test/java/com/awesome/testing/traffic/TrafficConfigTest.java`
8. Increased test context cache:
   - `src/test/resources/spring.properties`
9. Added class-level parallel execution and Mockito agent wiring in test plugins:
   - `pom.xml`
10. Implemented deterministic integration split:
   - default Surefire excludes heavy integration classes
   - `integration-tests` profile runs those classes via Failsafe
   - `pom.xml`
   - `src/test/java/com/awesome/testing/endpoints/ollama/OllamaChatControllerTest.java`
   - `src/test/java/com/awesome/testing/endpoints/ollama/OllamaGenerateControllerTest.java`
   - `src/test/java/com/awesome/testing/endpoints/traffic/TrafficControllerTest.java`
11. Upgraded Maven wrapper distribution:
   - `.mvn/wrapper/maven-wrapper.properties`
