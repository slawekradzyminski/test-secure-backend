# RestClient Test Migration Plan

## Goal

Replace legacy `TestRestTemplate` usage in tests with `RestClient` and keep test behavior unchanged.

## Why

- `RestClient` is the modern synchronous HTTP API in Spring.
- Cleaner fluent API with typed `toEntity(...)` handling.
- Reduces dependency on `spring-boot-resttestclient`.

## Migration Order (Class-by-Class)

1. `HttpHelper` (base test HTTP abstraction)  
   Status: done
2. `AbstractOllamaTest` (event-stream helper)  
   Status: done
3. Endpoint test classes using `HttpHelper`  
   Status: no code changes needed after helper migration, but verify class-by-class
4. Remove old test dependency (`spring-boot-resttestclient`)  
   Status: done

## New API Pattern

```java
ResponseEntity<MyDto> response = restClient()
    .method(HttpMethod.POST)
    .uri("/api/example")
    .headers(h -> h.addAll(headers))
    .body(request)
    .retrieve()
    .toEntity(MyDto.class);
```

## Validation Checklist

- `./mvnw test`
- `./mvnw verify`
- smoke-check a few endpoint suites:
  - users
  - orders
  - ollama

