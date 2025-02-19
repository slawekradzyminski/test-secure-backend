# Implementation Plan for Proxying Ollama's Streaming Endpoint

## 1. Purpose

We need a new controller in the backend to handle requests from the frontend and forward them to the local Ollama server running at `http://localhost:11434`. Specifically, we are interested in the **streaming** `POST /api/generate` endpoint provided by Ollama, as documented in [Ollama's API docs](https://github.com/ollama/ollama/blob/main/docs/api.md). 

We also want the frontend to display the response **as it streams**, rather than collecting the entire response before returning it. This means our backend proxy should maintain **chunked** or **server-sent** streaming behavior so that partial data from Ollama can be forwarded in real time.

## 2. Components to Implement

### A. Configuration
```java
@ConfigurationProperties(prefix = "ollama")
@Validated
public class OllamaProperties {
    @NotBlank
    private String baseUrl = "http://localhost:11434";
    // other properties
}
```

### B. DTOs
Location: `com.awesome.testing.dto.ollama`

```java
public record GenerateRequestDto(
    @NotBlank String model,
    @NotBlank String prompt,
    Boolean stream,
    Map<String, Object> options
) {}

public record GenerateResponseDto(
    String model,
    String created_at,
    String response,
    boolean done,
    Long[] context,
    Long total_duration
) {}
```

### C. Service Layer
```java
@Service
@RequiredArgsConstructor
public class OllamaService {
    private final WebClient webClient;
    
    public Flux<GenerateResponseDto> generateText(GenerateRequestDto request) {
        return webClient.post()
            .uri("/api/generate")
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(GenerateResponseDto.class);
    }
}
```

### D. Controller
```java
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OllamaController {
    private final OllamaService ollamaService;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ROLE_CLIENT') or hasRole('ROLE_ADMIN')")
    public Flux<GenerateResponseDto> generateText(@Valid @RequestBody GenerateRequestDto request) {
        return ollamaService.generateText(request);
    }
}
```

## 3. Implementation Steps

### A. Setup & Configuration
1. Add dependencies to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

2. Configure WebClient bean:
```java
@Configuration
@RequiredArgsConstructor
public class OllamaConfig {
    private final OllamaProperties properties;

    @Bean
    public WebClient ollamaWebClient() {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
```

### B. Security Integration
1. Add security configuration for new endpoints:
```java
.requestMatchers("/api/ollama/**").authenticated()
```

2. Ensure JWT filter processes these requests
3. Add rate limiting configuration if needed

## 4. Testing Strategy

### A. Unit Tests
Location: `src/test/java/com/awesome/testing/service`

```java
@SpringBootTest
class OllamaServiceTest {
    @MockBean
    private WebClient webClient;
    
    @Autowired
    private OllamaService ollamaService;
    
    @Test
    void shouldStreamResponse() {
        // given
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock();
        WebClient.RequestBodySpec requestBodySpec = mock();
        WebClient.RequestHeadersSpec requestHeadersSpec = mock();
        WebClient.ResponseSpec responseSpec = mock();
        
        // when
        when(webClient.post()).thenReturn(requestHeadersUriSpec);
        // ... setup other mocks
        
        // then
        StepVerifier.create(ollamaService.generateText(request))
            .expectNextMatches(response -> response.model().equals("llama2"))
            .expectNextMatches(response -> !response.done())
            .expectNextMatches(response -> response.done())
            .verifyComplete();
    }
}
```

### B. Integration Tests with WireMock
Location: `src/test/java/com/awesome/testing/endpoints/ollama`

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
class OllamaControllerTest {
    
    @Test
    void shouldStreamResponseWithValidToken() {
        // given
        stubFor(post(urlEqualTo("/api/generate"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"model":"llama2","response":"First","done":false}
                    {"model":"llama2","response":"Second","done":false}
                    {"model":"llama2","response":"Final","done":true}
                    """)
                .withChunkedDribbleDelay(3, 100)));
                
        // when
        webTestClient.post().uri("/api/ollama/generate")
            .header("Authorization", "Bearer " + validToken)
            .bodyValue(new GenerateRequestDto("llama2", "test prompt", true, null))
            .exchange()
            
        // then
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM_VALUE)
            .returnResult(GenerateResponseDto.class)
            .getResponseBody()
            .as(StepVerifier::create)
            .expectNextCount(3)
            .verifyComplete();
    }
}
```

## 5. Error Handling

### A. Error Response Format
```java
public record ErrorResponse(
    int status,
    String message,
    String timestamp
) {}
```

### B. Exception Handler
```java
@RestControllerAdvice
public class OllamaExceptionHandler {
    
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientResponseException(WebClientResponseException ex) {
        return ResponseEntity
            .status(ex.getStatusCode())
            .body(new ErrorResponse(
                ex.getStatusCode().value(),
                ex.getMessage(),
                Instant.now().toString()
            ));
    }
}
```

## 6. API Documentation

### OpenAPI Specification
```java
@Operation(summary = "Generate text using Ollama model")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Successful generation"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "422", description = "Invalid request"),
    @ApiResponse(responseCode = "500", description = "Ollama server error")
})
```

## 7. Next Steps
1. ✅Review and approve implementation plan
2. Set up project dependencies
3. Implement configuration and DTOs
4. Implement service layer with WebClient
5. Implement controller with security
6. Write tests
7. Add OpenAPI documentation
8. Perform security review
9. Test with frontend integration

## 8. Frontend Integration Example
```javascript
const eventSource = new EventSource('/api/ollama/generate', {
    headers: {
        'Authorization': `Bearer ${token}`
    }
});

eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    // Update UI with streaming response
    updateUI(data.response);
    if (data.done) {
        eventSource.close();
    }
};
```