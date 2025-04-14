# Refactoring Plan: Migrating from Single /process Endpoint to Specialized Endpoints

Below is a comprehensive, step-by-step refactoring plan for migrating from a single `/process` endpoint on the Python sidecar to three specialized endpoints—`/embeddings`, `/attention`, and `/reduce`. This plan is written in exhaustive detail to help an AI agent (like Claude 3.7) reliably perform the refactor. It includes everything from renaming service classes to updating request/response DTOs, adding new tests, and cleaning up references to the old route.

## 1. High-Level Overview

### 1.1 Why Split /process?

**Current situation**: The SidecarService calls a single `/process` endpoint to retrieve embeddings, attention, and optionally dimensionally reduced embeddings.

**New requirement**: The Python sidecar has been updated to provide three separate endpoints:
- POST `/embeddings` for standard token embeddings,
- POST `/attention` for multi-head attention weights, and
- POST `/reduce` for dimensionally reduced embeddings.

**Goal**: Update the Java backend so that it calls the correct specialized endpoint depending on whether the user wants embeddings, attention, or dimensionally reduced embeddings.

### 1.2 End Result

By the end of this refactor, our Java backend will:

- Have separate request DTOs and response DTOs for each of the three sidecar routes.

- Provide separate service methods in SidecarService (or multiple services) that call the Python sidecar routes:
  - `getEmbeddings(...)` calls POST `/embeddings`
  - `getAttention(...)` calls POST `/attention`
  - `reduceEmbeddings(...)` calls POST `/reduce`

- Possibly add a new controller or new endpoints in EmbeddingsController (or rename it to SidecarController) so that the client can request exactly what they want from each specialized route.

- Maintain or create separate integration tests for each specialized route, following the project's existing test strategy.

## 2. Creating New DTO Classes

### 2.1 Remove or Deprecate the Old SidecarRequestDto and SidecarResponseDto

- Locate SidecarRequestDto in `com.awesome.testing.dto.embeddings.SidecarRequestDto`.
- Locate SidecarResponseDto in `com.awesome.testing.dto.embeddings.SidecarResponseDto`.
- Decide if you want to remove them entirely or mark them as deprecated. Since the sidecar no longer supports `/process`, removing them is most likely the best choice.
- (If your code references them in many places, you may keep them until you have replaced all usages with the new classes, then remove them once the refactor is complete.)

### 2.2 Create the Three New Request DTOs

According to the new sidecar's OpenAPI spec:

#### EmbeddingsRequestDto

Fields:
- text (String; required)
- modelName (String; optional, default = "gpt2")

Example class:
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmbeddingsRequestDto {
    @NotBlank
    private String text;
    private String modelName = "gpt2";
}
```

#### AttentionRequestDto

Fields:
- text (String; required)
- modelName (String; optional, default = "gpt2")

Example class:
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttentionRequestDto {
    @NotBlank
    private String text;
    private String modelName = "gpt2";
}
```

#### ReduceRequestDto

Fields:
- text (String; required)
- modelName (String; optional, default = "gpt2")
- reductionMethod (String; optional, e.g. "pca" or "umap")
- nComponents (int; optional, typically 2 or 3)

Example class:
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReduceRequestDto {
    @NotBlank
    private String text;
    private String modelName = "gpt2";
    private String reductionMethod = "pca";
    private int nComponents = 2;
}
```

### 2.3 Create the Three New Response DTOs

From the sidecar's OpenAPI spec:

#### EmbeddingsResponseDto
Returns tokens and embeddings:

```java
@Data
public class EmbeddingsResponseDto {
    private List<String> tokens;
    private List<List<Float>> embeddings;
    private String modelName;
}
```

#### AttentionResponseDto
Returns tokens and attention weights:

```java
@Data
public class AttentionResponseDto {
    private List<String> tokens;
    // shape [num_layers][num_heads][num_tokens][num_tokens]
    private List<List<List<List<Float>>>> attention;
    private String modelName;
}
```

#### ReduceResponseDto
Returns tokens and dimensionally reduced embeddings:

```java
@Data
public class ReduceResponseDto {
    private List<String> tokens;
    private List<List<Double>> reducedEmbeddings;
    private String modelName;
}
```

(Adjust the field types to match the exact shape from the sidecar's JSON if needed.)

## 3. Updating the SidecarService Layer

### 3.1 Remove or Deprecate processText Method

You may rename processText to something like getEmbeddings or just remove it entirely. The old code that calls `/process` should be replaced by new methods that call the new endpoints.

### 3.2 Add Three New Methods

In SidecarService (in `com.awesome.testing.service.SidecarService`), create three distinct methods:

#### getEmbeddings

```java
public Mono<EmbeddingsResponseDto> getEmbeddings(EmbeddingsRequestDto request) {
    return embeddingsWebClient.post()
        .uri("/embeddings")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(EmbeddingsResponseDto.class)
        // add timeouts, retries, logging, etc.
        ;
}
```

#### getAttention

```java
public Mono<AttentionResponseDto> getAttention(AttentionRequestDto request) {
    return embeddingsWebClient.post()
        .uri("/attention")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(AttentionResponseDto.class);
}
```

#### reduceEmbeddings

```java
public Mono<ReduceResponseDto> reduceEmbeddings(ReduceRequestDto request) {
    return embeddingsWebClient.post()
        .uri("/reduce")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(ReduceResponseDto.class);
}
```

Note: Add `.timeout(...)`, `.retry(...)`, `.doOnError(...)`, or `.doOnSuccess(...)` logic as needed to match the existing style of SidecarService.

## 4. Exposing the New Endpoints in a Controller

### 4.1 Decide Controller Layout

We have a EmbeddingsController already. That might be a good place to handle all three new functionalities. Alternatively, you could create separate controllers:

- EmbeddingsController for `/api/embeddings`
- AttentionController for `/api/attention`
- ReduceController for `/api/reduce`

If you want a single controller with separate routes, you might do:

```java
@RestController
@RequestMapping("/api/sidecar")
public class SidecarController {
    // define /embeddings, /attention, /reduce below
}
```

### 4.2 Write Controller Methods

Within your chosen controller(s), define something like:

```java
@PostMapping("/embeddings")
public Mono<EmbeddingsResponseDto> getEmbeddings(@Valid @RequestBody EmbeddingsRequestDto requestDto) {
    return sidecarService.getEmbeddings(requestDto);
}

@PostMapping("/attention")
public Mono<AttentionResponseDto> getAttention(@Valid @RequestBody AttentionRequestDto requestDto) {
    return sidecarService.getAttention(requestDto);
}

@PostMapping("/reduce")
public Mono<ReduceResponseDto> reduceEmbeddings(@Valid @RequestBody ReduceRequestDto requestDto) {
    return sidecarService.reduceEmbeddings(requestDto);
}
```

(Make sure to add `@SecurityRequirement` and `@Operation` / `@ApiResponse` as done in your existing code.)

## 5. Removing All References to the Old /process Endpoint

- Search the entire codebase for references to `.uri("/process")`.
- Refactor calls to point to your new methods, getEmbeddings, getAttention, or reduceEmbeddings, whichever is appropriate for the business logic.
- Delete the old method SidecarService#processText(...) once everything that used it has been migrated.

## 6. Adapting or Creating Tests

Your existing test approach is thorough, with integration-level endpoint tests, so proceed accordingly:

### 6.1 Basic Test Strategy Recap

Organize tests by feature. Each endpoint has its own test class, e.g.:
- EmbeddingsControllerTest
- AttentionControllerTest
- ReduceControllerTest

Use the same pattern of:
- shouldReturn200WhenValidInput()
- shouldReturn400WhenBadRequest()
- shouldReturn401WhenUnauthorized()
- shouldReturn5XXWhenServerFails()

### 6.2 Unit Tests for SidecarService

Create or adapt a new SidecarServiceTest.java.
- testGetEmbeddingsSuccessfully()
- testGetAttentionSuccessfully()
- testReduceEmbeddingsSuccessfully()
- testSidecarServiceErrorResponse() (simulates WebClientResponseException with a 500, for instance)

Sample skeleton for testGetEmbeddingsSuccessfully:

```java
@Test
void testGetEmbeddingsSuccessfully() {
    EmbeddingsRequestDto request = new EmbeddingsRequestDto("Hello world", "gpt2");
    EmbeddingsResponseDto mockResponse = new EmbeddingsResponseDto(
       List.of("Hello", "world"),
       List.of(List.of(0.1f, 0.2f), List.of(0.3f, 0.4f)),
       "gpt2"
    );

    // Mock the WebClient response
    when(responseSpec.bodyToMono(EmbeddingsResponseDto.class)).thenReturn(Mono.just(mockResponse));

    Mono<EmbeddingsResponseDto> responseMono = sidecarService.getEmbeddings(request);

    StepVerifier.create(responseMono)
        .expectNext(mockResponse)
        .verifyComplete();
}
```

### 6.3 Controller Integration Tests

For each new controller route (`/api/embeddings`, `/api/attention`, `/api/reduce`), create or update test classes:
- EmbeddingsControllerTest.java
- AttentionControllerTest.java
- ReduceControllerTest.java

Use the same approach:
- shouldGet200WhenAuthorizedAndValidInput()
  - get a JWT token, call the endpoint with valid JSON, verify 200 response.
- shouldGet400WhenInvalidJson()
  - pass incomplete or invalid JSON, verify 400 response.
- shouldGet401WhenNoAuthorization()
  - do not pass a token, verify 401.
- shouldGet5XXWhenSidecarFails()
  - mock the sidecar returning 500 or 502, verify the response is 500 or 502 from your controller.

### 6.4 Removing Old /process Endpoint Tests

Locate any test named like `test-embeddings-endpoint.sh`, `testSidecarProcessEndpoint()`, or `SidecarControllerTest#shouldCallProcessText()`. Delete or rewrite them to use the new endpoints.

## 7. Additional Considerations

### Update Docker or Deployment
If the sidecar is now using different routes, ensure that your Docker Compose references are correct. The sidecar image might not have the `/process` route anymore.

### Swagger/OpenAPI
Update the Java app's swagger doc. The new endpoints in your Java backend should be properly documented with `@Operation`, `@ApiResponses`, etc.

### Logging & Observability
Because you're dealing with additional endpoints, consider adding logging statements that clarify which route is being called from the Java side.

### Performance
If your new calls are more granular, you might see more frequent round-trips. Confirm you have enough concurrency and connection pooling in your WebClient setup.

## 8. Final Cleanup

- Remove any references to SidecarRequestDto, SidecarResponseDto, or processText logic.
- Double-check all tests pass with:
  ```bash
  mvn test
  ```
- Commit your changes. The old single-route approach is no longer needed.

## 9. Conclusion

Following these instructions step by step will give you:

- Three new request/response DTO pairs (EmbeddingsRequestDto/EmbeddingsResponseDto, AttentionRequestDto/AttentionResponseDto, ReduceRequestDto/ReduceResponseDto).
- Three new service calls (getEmbeddings(...), getAttention(...), reduceEmbeddings(...)) in SidecarService.
- A clean set of endpoints in the controller(s) or a single multi-route EmbeddingsController.
- Fully updated test coverage using the established test patterns in your codebase.
- No leftover references to the outdated `/process` endpoint.

By carefully following these steps, Claude 3.7 (or any LLM that is guided properly) can reliably produce the required code changes, ensuring that each new endpoint is integrated into the existing security, logging, and test frameworks.