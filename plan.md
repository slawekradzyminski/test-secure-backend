# WebSocket-Based HTTP Traffic Monitoring System

This document outlines a comprehensive plan for implementing a WebSocket-based solution for capturing real-time HTTP traffic in a Spring Boot application.

## 1. Overall WebSocket Architecture

### HTTP Traffic Capture
- Use a Spring Filter or HandlerInterceptor to intercept each incoming request and outgoing response
- Record relevant metadata: HTTP method, URI, status code, timestamps, etc.

### In-Memory Storage
- Maintain a concurrent queue or list in the application to hold these captured traffic events temporarily

### WebSocket + STOMP
- Configure Spring Boot's built-in WebSocket support with STOMP (using `@EnableWebSocketMessageBroker`)
- Create a "traffic" destination, e.g. `/topic/traffic`
- Whenever a new event is intercepted, broadcast it to `/topic/traffic`

## 2. Detailed Implementation Steps

### 2.1 Model for Traffic Events
Create a DTO class, e.g. `TrafficEventDto`, that holds relevant metadata:

```java
// package com.awesome.testing.dto.traffic
@Data
@Builder
public class TrafficEventDto {
    private String method;
    private String path;
    private int status;
    private long durationMs;
    private Instant timestamp;
    // optional fields: requestBody, responseBody, headers, etc.
}
```

Decide whether to include request/response body details – be mindful of sensitive data.

### 2.2 Create a Concurrent Data Structure
In a Spring `@Configuration` class, define a bean:

```java
// package com.awesome.testing.traffic
@Configuration
public class TrafficConfig {

    @Bean
    public ConcurrentLinkedQueue<TrafficEventDto> trafficQueue() {
        return new ConcurrentLinkedQueue<>();
    }
}
```

This queue will hold newly captured events. Another approach is to broadcast them immediately from the filter, but a queue can help coordinate concurrency or store a short backlog.

### 2.3 HTTP Traffic Interceptor (Filter or HandlerInterceptor)

#### Option A: Filter Approach
- Implement `jakarta.servlet.Filter` (for Spring Boot 3.x)
- Inject your `ConcurrentLinkedQueue<TrafficEventDto>` into this filter
- For each request:
  - Note `startTime = System.currentTimeMillis()`
  - Call `chain.doFilter(...)`
  - After the call returns, calculate `duration = System.currentTimeMillis() - startTime`
  - Collect method, path, status, etc., into `TrafficEventDto`
  - Add it to the queue or publish immediately to WebSocket (details in the next steps)

```java
// package com.awesome.testing.traffic
@Component
public class TrafficLoggingFilter implements Filter {

    private final ConcurrentLinkedQueue<TrafficEventDto> trafficQueue;

    public TrafficLoggingFilter(ConcurrentLinkedQueue<TrafficEventDto> trafficQueue) {
        this.trafficQueue = trafficQueue;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException {

        if (req instanceof HttpServletRequest httpReq && res instanceof HttpServletResponse httpRes) {
            long start = System.currentTimeMillis();
            chain.doFilter(req, res);
            long duration = System.currentTimeMillis() - start;

            // Build the event
            TrafficEventDto event = TrafficEventDto.builder()
                .method(httpReq.getMethod())
                .path(httpReq.getRequestURI())
                .status(httpRes.getStatus())
                .durationMs(duration)
                .timestamp(Instant.now())
                .build();

            // Add to queue (or broadcast immediately)
            trafficQueue.add(event);
        } else {
            chain.doFilter(req, res);
        }
    }
}
```

#### Option B: HandlerInterceptor Approach
Similar logic, but in `preHandle()` + `afterCompletion()`. Interceptors are more closely tied to Spring MVC. A filter is more general.

### 2.4 WebSocket Configuration with STOMP
In Spring Boot, you can enable STOMP messaging with annotations:

```java
// package com.awesome.testing.config
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // set application destination prefix if desired
        config.enableSimpleBroker("/topic"); // Simple in-memory broker
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The endpoint the client will connect to: e.g. /ws-traffic
        registry.addEndpoint("/ws-traffic")
                .setAllowedOrigins("*") // or refine for CORS
                .withSockJS(); // optional if you want SockJS fallback
    }
}
```

### 2.5 Broadcasting Messages

Typically, you'll create a Spring bean to hold a reference to `SimpMessagingTemplate`. Then, from the filter (or a scheduled job that polls the queue), you can do:

```java
@Autowired
private SimpMessagingTemplate messagingTemplate;

// ...
messagingTemplate.convertAndSend("/topic/traffic", eventDto);
```

Implementation Options:
- **Immediate Broadcast**: In the filter, immediately do `messagingTemplate.convertAndSend("/topic/traffic", eventDto)`
- **Queue + Scheduled**: Put into the queue, and have a scheduled method that drains the queue and sends each event to STOMP

For a simpler approach, you can inject `SimpMessagingTemplate` into the filter:

```java
// In the filter:
messagingTemplate.convertAndSend("/topic/traffic", event);
```

Note: concurrency issues are usually minimal unless you are under massive load.

### 2.6 WebSocket Broadcasting Example
Here's a small "TrafficPublisher" that can run asynchronously, if you want to separate filter logic from broadcast:

```java
// package com.awesome.testing.traffic
@Component
public class TrafficPublisher {

    private final ConcurrentLinkedQueue<TrafficEventDto> queue;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public TrafficPublisher(
         ConcurrentLinkedQueue<TrafficEventDto> queue,
         SimpMessagingTemplate messagingTemplate) {
        this.queue = queue;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void broadcastTraffic() {
        // drain the queue
        while (!queue.isEmpty()) {
            TrafficEventDto event = queue.poll();
            messagingTemplate.convertAndSend("/topic/traffic", event);
        }
    }
}
```

Note: This scheduled approach sends data every second in small batches. For real-time, you might set `fixedDelay` to 100ms or broadcast immediately from the filter. The tradeoff is overhead vs. near-instant feed.

## 3. Testing Strategy

### 3.1 Unit Testing the Filter

#### Mock HTTP Requests
- Use a library like Spring's `MockHttpServletRequest` / `MockHttpServletResponse`
- Pass them through your `TrafficLoggingFilter`
- Assert that the expected `TrafficEventDto` is created and placed into the queue or broadcast via `SimpMessagingTemplate`

#### Check Concurrency
- Potentially test adding many events in quick succession
- Confirm no concurrency exceptions are thrown

### 3.2 Testing WebSocket Broadcasting

#### Spring's STOMP Integration Tests
- Start the Spring context in test mode
- Use `org.springframework.messaging.simp.stomp.StompSession` and `StompSessionHandlerAdapter` to connect to your test server
- Send a real HTTP request to the server, confirm a `TrafficEventDto` is broadcast to `/topic/traffic`

## 4. Documentation

### README.md
- Explain how to start the application
- Document the WebSocket endpoint (e.g., `/ws-traffic`) and the subscribed topic `/topic/traffic`

### Swagger/OpenAPI
- Add notes about WebSocket endpoints since they don't appear automatically in OpenAPI documentation

### Code Comments
- In your filter, note any potential performance or security concerns
- Indicate that you might not want to include request body data in production logs

## 5. Security and Other Considerations

### Authentication
- Configure Spring Security for the WebSocket endpoint (e.g., require a valid JWT and user role)
- Restrict `@MessageMapping` or the STOMP topic to authorized roles

### Performance
- Logging every request can be intensive in high-load applications
- Possibly sample or limit logs if this is a production environment

### Sensitive Data
- Be careful if capturing request bodies (they might contain passwords, tokens, etc.)
- If storing them, ensure encryption at rest or remove them entirely

### Production vs. Demo
- This approach is great for demos or dev environments
- In production, you might need advanced strategies for multi-instance setups, message brokers (RabbitMQ, Kafka), etc.

## 6. Implementation Timeline
- **Model & Filter**: 1 hour – create `TrafficEventDto`, concurrency queue, and the filter
- **WebSocket Config**: 1 hour – create `WebSocketConfig` with STOMP, an endpoint, and a `/topic/traffic` destination
- **Broadcast Logic**: 30 min – Decide if immediate or scheduled broadcast. Implement using `SimpMessagingTemplate`
- **Testing & Documentation**: 1-2 hours – unit tests (filter & WebSocket), integration test with STOMP, update README

## 7. Final Summary

By combining:
- A Spring Filter to capture requests and responses
- A concurrent queue or direct broadcast approach to store events
- A Spring STOMP WebSocket endpoint (`/ws-traffic`)

...you achieve a real-time feed of all HTTP traffic. This feed can be automatically updated on every new request. It is ideal for debugging, teaching demonstrations, or admin monitoring. Adjust data capture depth, security controls, and performance strategies as necessary.