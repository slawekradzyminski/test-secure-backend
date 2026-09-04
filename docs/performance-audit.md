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
