# Detailed Plan: Streamed Chat Endpoint Integration with Ollama

This plan describes how to create a new `/api/ollama/chat` endpoint that forwards chat requests to Ollama's `POST /api/chat` endpoint and then streams the partial responses back to the client using Server-Sent Events (SSE).

## 1. Overview

- **Goal**: Implement a chat API that supports multi-message context, returns partial responses as soon as they're available, and continues streaming until complete.
- **Key Components**:
  1. **DTOs** for request/response payloads
  2. **Service** method to forward requests to Ollama and handle streaming
  3. **Controller** to expose the new `/api/ollama/chat` endpoint and return SSE events

## 2. Create Data Transfer Objects (DTOs)

### 2.1 `ChatRequestDto`
Purpose: Holds the necessary fields for sending a chat request to Ollama.

```java
package com.awesome.testing.dto.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDto {

    /**
     * The model name to invoke (e.g., "llama3.2:1b").
     */
    @NotBlank
    private String model;

    /**
     * A list of messages representing the conversation history.
     * Each message has a role ("system", "user", "assistant") and some text content.
     */
    private List<ChatMessageDto> messages;

    /**
     * Optional model parameters (e.g., temperature, maxTokens, etc.).
     */
    private Map<String, Object> options;

    /**
     * Whether the Ollama response should be streamed.
     * Defaults to true if omitted.
     */
    private Boolean stream;

    /**
     * How long Ollama should keep the model loaded in memory after the request (in minutes).
     * Optional.
     */
    private Integer keepAlive;
}
```

### 2.2 ChatMessageDto
Purpose: Represents a single message within the conversation.

java
Copy
Edit
```java
package com.awesome.testing.dto.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    /**
     * The role of this message in the conversation (e.g., "system", "user", or "assistant").
     */
    @NotBlank
    private String role;

    /**
     * The text content of the message.
     */
    @NotBlank
    private String content;
}
```

### 2.3 ChatResponseDto
Purpose: Mirrors each partial chunk in Ollama's streaming response. The format can look like:

json
Copy
Edit
```json
{
  "model": "llama3.2:1b",
  "created_at": "2025-02-22T11:12:46.524963888Z",
  "message": {
    "role": "assistant",
    "content": " Rayleigh"
  },
  "done": false
}
```

java
Copy
Edit
```java
package com.awesome.testing.dto.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {

    private String model;

    @JsonProperty("created_at")
    private String createdAt;

    /**
     * The partial or final message chunk returned by Ollama.
     * Typically contains role and content.
     */
    private ChatMessageDto message;

    /**
     * Indicates whether this is the final chunk in the response.
     */
    private boolean done;
}
```

## 3. Extend Service Layer for Chat
Create or extend an existing service (e.g., OllamaService) to:

Send a POST request to "/api/chat" on Ollama's endpoint.
Handle the streaming response via Flux<ChatResponseDto>.
Log or handle any errors encountered.

java
Copy
Edit
```java
package com.awesome.testing.service;

import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaService {

    private final WebClient ollamaWebClient;

    public Flux<ChatResponseDto> chat(ChatRequestDto request) {
        // Default stream to true if omitted
        boolean streamEnabled = (request.getStream() == null) ? true : request.getStream();

        log.info("Sending chat request to model: {}, streaming: {}", request.getModel(), streamEnabled);

        return ollamaWebClient.post()
            .uri("/api/chat")
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(ChatResponseDto.class)
            .doOnNext(chunk -> {
                log.debug("Received chunk: role={}, content={}",
                          chunk.getMessage().getRole(),
                          chunk.getMessage().getContent());
            })
            .doOnError(ex -> {
                log.error("Error during chat streaming: {}", ex.getMessage(), ex);
            })
            .doOnComplete(() -> {
                log.info("Chat streaming completed for model: {}", request.getModel());
            });
    }
}
```

Notes:

- ollamaWebClient should be configured in a separate config class (OllamaConfig) with sufficient timeouts for streaming.
- The bodyToFlux(ChatResponseDto.class) call instructs Spring WebFlux to parse each JSON object from Ollama as a ChatResponseDto.

## 4. Create the Chat Controller
Expose a POST endpoint at /api/ollama/chat. By returning a Flux<ChatResponseDto> with produces = MediaType.TEXT_EVENT_STREAM_VALUE, Spring automatically sets up SSE.

java
Copy
Edit
```java
package com.awesome.testing.controller;

import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import com.awesome.testing.service.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
public class OllamaChatController {

    private final OllamaService ollamaService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponseDto> chat(@Validated @RequestBody ChatRequestDto request) {
        log.info("Initiating chat request: model={}", request.getModel());
        return ollamaService.chat(request);
    }
}
```

Example Curl Request
bash
Copy
Edit
```bash
curl -X POST http://localhost:4001/api/ollama/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.2:1b",
    "messages": [
      {
        "role": "user",
        "content": "Why is the sky blue?"
      }
    ],
    "stream": true,
    "options": {
      "temperature": 0.7
    }
  }'
```

Example SSE Output
kotlin
Copy
Edit
```
data: {"model":"llama3.2:1b","created_at":"2025-02-22T11:12:46.524963888Z","message":{"role":"assistant","content":" Rayleigh"},"done":false}

data: {"model":"llama3.2:1b","created_at":"2025-02-22T11:12:46.636500343Z","message":{"role":"assistant","content":" Scattering"},"done":false}
```
(The final chunk would typically have "done": true.)

## 5. Chat History (Optional)
Approach: The client can include all previous messages in every request. Ollama uses the messages array to maintain conversation context.
Alternative: Store messages server-side (in memory or a database) if you want to manage sessions yourself. That's beyond the immediate scope.

## 6. Testing & Validation
Integration Testing:

- Write tests in src/test/java with WebTestClient or MockMvc.
- Start a local Ollama instance (or use WireMock to mock /api/chat).
- Verify that partial chunks arrive and the final chunk has "done": true.

Error Handling:

- If Ollama returns 4xx or 5xx errors, handle them in .onStatus() or catch the exception in .doOnError(...).
- Return a meaningful response or pass it through to the client.