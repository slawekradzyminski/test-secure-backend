# Product search

`GET /api/v1/products/search` requires a bearer token, like `GET /api/v1/products`
(401 without one). `GET /api/v1/products` is unchanged: a bare array with no
parameters. GraphQL is unchanged.

## Parameters

All parameters are optional. Blank values count as absent.

| Parameter | Meaning |
|---|---|
| `q` | Trimmed text, at most 100 characters. Case-insensitive substring match on name, category or description. `%`, `_` and `\` are literal characters. |
| `category` | Case-insensitive exact category. Repeat the parameter or separate values with commas; at most 20 values. Products in any listed category match. |
| `minPrice`, `maxPrice` | Inclusive bounds, zero or greater; `minPrice` must not exceed `maxPrice`. |
| `minRating` | 1–5. Average of **approved** reviews must be at least this value (unrounded). Products without approved reviews are excluded. |
| `inStock` | `true` keeps products with stock above zero. `false` or absent applies no stock filter, like the `inStockOnly` catalog filter. |
| `sort` | `relevance` (default when `q` is given), `newest` (default otherwise; also used for `relevance` without `q`), `price_asc`, `price_desc`, `name_asc`, `rating_desc`. Case-insensitive. |
| `page` | Zero-based page index, default 0. A page past the end returns empty `content` with the real totals. |
| `size` | 1–100, default 20. |

Ordering and tie-breakers:

- `relevance`: score = 4 for a name match + 2 for a category match + 1 for a
  description match, highest first, then name (case-insensitive), then id.
- `newest`: `createdAt` descending, then id descending.
- `price_asc` / `price_desc`: price, then id ascending.
- `name_asc`: case-insensitive name, then id.
- `rating_desc`: average approved rating (unrated products count as 0), then
  approved review count, then name, then id.

## Response

```json
{
  "content": [
    {
      "id": 1, "name": "Armchair", "description": "Comfortable chair", "price": 120.00,
      "stockQuantity": 2, "category": "Chairs", "imageUrl": null,
      "createdAt": "2026-11-10T10:30:02.809462", "updatedAt": "2026-11-10T10:30:02.809462",
      "averageRating": 4.5, "reviewCount": 2
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "facets": {
    "categories": [{ "value": "Chairs", "count": 1 }, { "value": "Misc", "count": 3 }],
    "priceRange": { "min": 10.00, "max": 300.00 },
    "inStockCount": 3
  }
}
```

- Items carry every `ProductDto` field plus `averageRating` (approved reviews,
  rounded half-up to one decimal, `null` without approved reviews — the same
  rule as `GET /products/{id}/reviews/summary`) and `reviewCount`.
- **Facets are computed over the products matching every filter except
  `category`** (`q`, prices, `minRating` and `inStock` still apply), so the
  category facet shows the alternatives to the current selection. `priceRange`
  and `inStockCount` use the same set. Categories are ordered by count
  descending, then value. `priceRange.min`/`max` are `null` when nothing
  matches. Facets do not depend on `page`, `size` or `sort`.

## Errors

Invalid parameters return 400 with a `ValidationErrorsDto` field map keyed by
parameter name, reporting every problem at once, for example
`{"minPrice":"must not be greater than maxPrice","sort":"must be one of relevance, newest, price_asc, price_desc, name_asc, rating_desc"}`.
Wrong types (`minPrice=cheap`, `inStock=maybe`, `size=1.5`) use the same shape
(`must be a number`, `must be true or false`, `must be an integer`).

## Implementation

- `ProductSearchCriteria` parses and validates the parameters (scalars are bound
  as strings so type errors are keyed by parameter name too).
- `ProductSearchSpecifications` builds a `Specification<ProductEntity>` used with
  `ProductRepository` (`JpaSpecificationExecutor`). Text matching is
  `lower(column) LIKE ? ESCAPE '\'`; ratings are correlated `avg`/`count`
  subqueries over approved reviews; relevance is a sum of `CASE` expressions.
  Ordering lives in the specification because relevance and rating are
  expressions, so the page request is unsorted. All of it is standard SQL and
  runs on H2 (tests) and PostgreSQL (`ProductSearchPostgresIT`).
- `ProductSearchQueries` runs one grouped query for the facets
  (`category`, `count`, `min(price)`, `max(price)`, in-stock sum) and one
  `group by product` query for the ratings of the returned page, so there is
  no N+1.
- `V15__product_search_indexes.sql` (PostgreSQL) adds indexes for the
  `name_asc`, price and `newest` orderings. Substring matching cannot use a
  B-tree index; the catalog is small enough for a sequential scan.
