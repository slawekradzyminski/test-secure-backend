# Product catalog pagination performance

Measured locally on 2026-09-04 using H2, Hibernate statistics, and a 2,000-product fixture.
The affected path is `ProductService.listProducts`, used by the LLM `list_products` tool.

| Request | Before: entities loaded | After: entities loaded | SQL queries |
| --- | ---: | ---: | ---: |
| offset 1,903, limit 25 | 1,928 | 25 | 2 before and after |

The previous implementation fetched `offset + limit` rows from the beginning, mapped
all of them to DTOs, and sliced the result in memory. Pagination now applies the
actual row offset and limit in SQL, with stable ascending ID order. The query also
omits `DISTINCT`: it selects products without any joins that could duplicate rows.

This reduces entity loading and DTO conversion by 98.7% for the measured request.
It is not an end-to-end latency benchmark or a PostgreSQL query-plan measurement.
Database offset traversal and the total-count query still depend on catalog size.
The complete-catalog HTTP endpoint retains its existing behavior.

Reproduce the regression checks:

```sh
./mvnw -Dtest=ProductPaginationTest test
```

The deep-offset test was first run against the original implementation and failed
with 1,928 entities loaded instead of 25. The fixed implementation passes. Other
cases cover category and stock filters, non-page-aligned offsets, partial final
pages, offsets beyond the catalog (including `Integer.MAX_VALUE`), and input clamps.
The test asserts loaded entities rather than timing so slow machines do not cause
spurious performance failures. Its fixture is flushed and the persistence context
cleared before measurement.

Full local verification:

```sh
./mvnw verify
./mvnw -Pmutation-testing test-compile pitest:mutationCoverage \
  -DtargetClasses=com.awesome.testing.service.ProductService \
  -DtargetTests=com.awesome.testing.service.ProductServiceTest,com.awesome.testing.service.ProductPaginationTest
```

External course checks remain part of whole-stack verification in `awesome-localstack`.

Verification result: 416 tests passed, along with PMD and the JaCoCo coverage gate.
Targeted mutation testing killed 28 of 29 mutants, with no survivors. The remaining
`NO_COVERAGE` mutant is the existing missing-product exception supplier in the stock
update path (`updateProduct`), outside the pagination change; it is a reachability
gap, not an equivalent mutant.

## GraphQL and traffic audit — 2026-10-20

`CommercePerformanceTest` measures SQL work with Hibernate statistics; telemetry
persistence is mocked in that test so background INSERTs cannot contaminate the
measurement. One authentication lookup is excluded from the query counts below.
These are database-work regressions, not production latency benchmarks.

| GraphQL selection | Verified database work |
| --- | --- |
| `products(limit:1) { total items { id description price } }`, two products | One product SELECT plus one COUNT; one product entity loaded; no refetch |
| Aliased/fragmented product `total`, `items @skip(if:true)` | One COUNT; zero product entities loaded |
| Product `items` without `total` | One slice SELECT; no COUNT (a slice may read one lookahead row) |
| Order `total` | One COUNT; no order-item entities loaded |
| Order summaries without nested items or total | One slice SELECT; no COUNT or item loading |
| Order pages with product names | Query count stays constant from one to five distinct products/orders |

Selection flags come from GraphQL's normalized selection set, including aliases,
fragments and evaluated directives. Ownership and administrative checks still run
before count-only queries. Order item fetch joins also fetch their products,
removing the previous product-name N+1 lookup when mapping order pages.

Migration V5 adds order filter/sort indexes, foreign-key lookup indexes, a
case-folded product-category index, and a composite traffic session/time/id index.
Traffic list filtering now uses exact, case-sensitive session equality, matching
detail lookup and WebSocket topics; lowercasing session IDs could mix distinct
sessions and prevented straightforward use of the existing index. Timestamp ties
are resolved by descending ID. `CommerceIndexMigrationIT` upgrades V4 and exercises
PostgreSQL query plans with 20,000 rows per table. Existing minimal-schema token
migration tests remain applicable because V5 guards absent tables.

Traffic persistence is best effort and buffered independently of business requests:

- `app.traffic.persistence-queue-capacity`: 1,000 records by default.
- `app.traffic.persistence-batch-size`: 100 records per transaction.
- `app.traffic.persistence-flush-interval`: 100 ms by default.
- `app.traffic.event-queue-capacity`: 1,000 WebSocket events by default.
- Saturation drops the newest telemetry, increments `traffic.persistence.dropped`
  or `traffic.events.dropped`, and does not block the business request.
- A failed persistence batch is discarded and counted in `traffic.persistence.failed`;
  future batches can continue. Pending queue sizes are exposed as gauges.
- Log reads flush the bounded pending snapshot first, preserving immediate API
  read-back without requiring client sleeps. Graceful shutdown drains the queue;
  an abrupt process failure can lose buffered records.

`saveAll` provides one transaction per batch. Identity-generated IDs still require
individual INSERT statements; this change does not claim JDBC wire batching.
Log retention now uses one bulk DELETE rather than loading expired entities.
The WebSocket publisher limits work per tick and isolates delivery failures.
REST telemetry failures can no longer suppress a successful buffered response.
Diagnostic Actuator endpoints now require ROLE_ADMIN; existing public health,
info and Prometheus endpoints retain their access rules.

### Review repairs and modern APIs

The review also found PostgreSQL-specific payload problems: traffic bodies were
stored as large-object OIDs, so casting a body to text searched an object ID rather
than the body. Removing a log row did not unlink its large objects. Migration V6
converts the four payload/header columns to text, preserves their contents, and
unlinks the converted objects in the same transaction. A PostgreSQL-backed HTTP
regression verifies capture, immediate body search, and detail reads; the migration
regression verifies preservation of a pre-existing large-object payload and removal
of its old object. Untrusted URL/method/content-type metadata is bounded before
joining shared persistence batches, preventing one oversized request from poisoning
otherwise valid records in a batch.

Buffering, querying, specifications, JSON mapping, and servlet response handling
now have separate focused classes. The changed Java uses four-space formatting
and the existing Java 25 compiler target. Jackson 3 calls use `asString()`;
Jackson 2 OpenAPI tests retain their supported `asText()` API. Hibernate's deprecated
`@Check` annotations were replaced with Jakarta Persistence `@CheckConstraint`
without changing the database constraints.

The deprecated MFA encryption factory was replaced with the current AES-GCM API.
New ciphertext uses a `v2:` prefix and the current PBKDF2-HMAC-SHA256 derivation.
Unprefixed stored secrets retain an explicit legacy decryption path using their
original derivation. A fixed ciphertext produced by the retired implementation
proves backward readability; tampering is rejected without a fallback to legacy
keys. Existing secrets are not silently rewritten on reads.

Deployment requires a coordinated upgrade: stop old backend replicas before
applying V6 or allowing new MFA writes. Old binaries cannot read the text storage
mapping or v2 MFA ciphertext. Keep the existing MFA password and salt unchanged
when upgrading. No deployment or external production course test run is part of
this local change.

### Mutation review

The broad advisory run generated 253 mutants: 244 were killed by assertions and
2 nonterminating queue-drain mutants timed out. The run exposed meaningful gaps in
order-item fields and page metadata, optional traffic filters, capture body/header
content, overload counters, cookie handling, and immediate detail read-back. These
now have regressions that pass on the original code and detect the altered behavior.
The final narrow follow-up for recording/filtering/timing detected 55 of 56 mutants
with no reachability gaps. Request duration is now isolated in a small monotonic timer
and tested with a supplied clock, rather than wall-clock thresholds.

Reviewed residuals across the runs:

- Equivalent: clipping at an exactly equal field limit returns the same text.
- Equivalent: one extra empty flush after the bounded read snapshot performs no
  database operation and changes neither records nor counters.
- Equivalent for the fixed omitted-media list: negating the helper's omitted-image
  guard still produces the same `anyMatch` result because both image and non-image
  omitted types are present; exact media matches are handled first.
- Cleanup-only survivor: removing `PBEKeySpec.clearPassword()` cannot be observed by
  functional ciphertext tests. The cleanup remains in place; no implementation-detail
  assertion was added to manufacture a higher score.

There are no remaining meaningful authorization, money, sanitization, or state-change
survivors in the reviewed changed paths. The aggregate mutation percentage is not a
release gate, and the PostgreSQL migration and payload-storage checks are separate
from mutation testing.

Final verification: the full local run passed 590 unit tests and 44 integration
checks, including PostgreSQL payload migration/search and query plans. The final
case-insensitive binary-media guard was first reproduced with `IMAGE/JPEG`, then
fixed and verified by 25 focused recording/timing tests plus PMD. The formatting
check passed for all changed Java sources. Compilation with deprecation diagnostics
reported no deprecated project API calls; dependency/JDK native/Unsafe notices are
not project API usage. No deployment or production checks were performed.

### Local course compatibility verification — 2026-10-20

The unchanged `l20` course suite at revision
`c6f7aee44150ee3e46aa27aad7b340e729e8fada` was exercised against the current, uncommitted backend artifact
using `awesome-localstack/scripts/verify-course-compatibility.py`. The complete
revision is recorded in each report. Both runs passed all 311 tests with zero
skips, retries, flaky results, or runner errors:

| Local database | Passed | Configuration |
| --- | ---: | --- |
| H2 | 311 | Existing disposable compatibility profile |
| PostgreSQL 16.14 | 311 | Separate disposable databases, Flyway enabled, Hibernate schema validation |

The PostgreSQL run used a temporary Compose override; both backends applied
migrations V1–V6 successfully. Both runs used the same Java 25 candidate image,
unchanged test sources and assertions, and one Playwright worker. The existing
runner supplies disposable accounts, exact-valued product fixtures, and an Ollama
mock. Effective API targets were restricted to local ports 14001 and 14002;
neither production nor the existing local stack on port 8081 was exercised.

Reports, candidate artifact identity, temporary PostgreSQL configuration, and
migration evidence are saved under `target/course-compatibility/`. The runner
removed its disposable containers after each run. These results cover the course
API flows, including GraphQL, traffic, and MFA, but do not exercise mixed-version
deployment or replace the existing-data migration integration tests. The
coordinated-upgrade requirement above still applies.

### Backend 3.9.1: legacy null-byte previews

The first production upgrade exposed three historical traffic previews containing
NUL bytes. PostgreSQL rejected V6's conversion to text and rolled back that
migration atomically; the four payload columns remained OIDs. The previous stable
backend was restored against that compatible schema and both public sites became
healthy again. V5's additive indexes remained applied.

The repair renders NUL as the visible U+2400 symbol (`␀`) in traffic previews.
This changes the representation of that control character while preserving preview
character lengths, normal text, and Unicode. New persistence batches apply the same
normalization so one null-bearing capture cannot discard unrelated records.
The guarded `beforeMigrate.sql` callback prepares legacy OID contents before the
unchanged V6 migration, and does nothing once the columns are text. Existing MFA
keys and business records are unaffected. The encrypted pre-upgrade backup retains
the original historical payload bytes.

Regressions first reproduced the failed migration and unsafe queued payload, then
passed with leading, trailing, and repeated NULs across all four payload columns,
Unicode preservation, unchanged preview lengths, repeated migration, and actual
PostgreSQL capture/read-back. The full normal test run passed 592 unit and 46
integration tests; a parameter-reassignment style violation was repaired and the
focused persistence tests plus PMD passed. A private disposable copy of the actual
1116 traffic records, including the three NUL-bearing rows, converted successfully
to four text columns with zero remaining copied large objects. No payload contents
left the production host, and the rehearsal container was removed.
