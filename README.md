# Secure Backend Application

This is a secure backend application built with Spring Boot, featuring JWT authentication, PostgreSQL database, and
ActiveMQ messaging.

## Profiles

The application supports two profiles:

### Local Profile

The local profile uses H2 in-memory database and is suitable for development and testing.

To run with local profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Docker Profile

The Docker profile uses PostgreSQL and is suitable for production-like environments.

To run with Docker profile:

```bash
docker compose up --build
```

This will start:

- Backend service on port 4001
- Frontend service on port 8081
- PostgreSQL database on port 5432
- ActiveMQ on ports 61616 (broker) and 8161 (web console)

## Database Access

### Accessing PostgreSQL Database

You can interact with the PostgreSQL database in several ways:

1. Using docker exec and the psql command-line tool:

```bash
# Connect to the database
docker exec -it test-secure-backend-postgres-1 psql -U postgres -d testdb

# Common psql commands:
\dt                 # List tables
\d table_name       # Describe table
\q                  # Quit psql

# Example queries:
SELECT * FROM app_user;
SELECT * FROM products;
SELECT * FROM cart_items;
SELECT * FROM orders;
```

2. Using external tools:
    - Host: localhost
    - Port: 5432
    - Database: testdb
    - Username: postgres
    - Password: postgres

### Database Schema

The main tables in the database:

- `app_user`: Stores user information
- `products`: Stores product catalog
- `cart_items`: Stores shopping cart items
- `orders`: Stores order information

## API Documentation

The API documentation is available at:

- Swagger UI: [http://localhost:4001/swagger-ui/index.html](http://localhost:4001/swagger-ui/index.html)
- OpenAPI JSON: [http://localhost:4001/v3/api-docs](http://localhost:4001/v3/api-docs)

## Initial Data

The application automatically sets up initial data when started:

- Admin users (username/password):
    - admin/admin
    - admin2/admin2
- Client users:
    - client/client
    - client2/client2
    - client3/client3
- Sample products in various categories

## Security

The application uses JWT tokens for authentication. To access protected endpoints:

1. Get a token using the `/users/signin` endpoint
2. Include the token in the Authorization header: `Bearer <token>`

Sign in responses now include both an access token and a refresh token. The refresh token can be exchanged via `POST /users/refresh` even when the access token expires, and calling `POST /users/logout` revokes the refresh token on the server.

## Features

- User authentication with JWT tokens
- Role-based authorization (ADMIN and CLIENT roles)
- User management (signup, signin, edit, delete)
- Email sending functionality via ActiveMQ
- Ollama integration for AI text generation and chat
- Product management
- Shopping cart functionality
- Order management
- Swagger/OpenAPI documentation
- Comprehensive test coverage

## Testing & Coverage

- Run `./mvnw verify` to execute the entire unit/integration suite. The build fails if line coverage drops below 40%, and JaCoCo reports are emitted to `target/site/jacoco/index.html`.
- Core scenarios are covered with focused unit tests for business services (users, products, carts, orders, email), security components (token provider, filter, authentication handler, security config), and controller utilities/exception handlers.
- Repository-specific behavior is validated with `@DataJpaTest` suites for `OrderRepository` and `CartItemRepository`, ensuring the custom JPQL queries behave correctly against H2.

## Getting Started

### Prerequisites

- Java 25 (Temurin distribution recommended)
- Maven 3.x
- ActiveMQ (for email functionality)

### Running the Application

1. Clone the repository
2. Configure ActiveMQ connection in `application.yml`
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`

### Running Tests

```bash
mvn test
```

## API Endpoints

### Authentication

- POST `/users/signin` - Authenticate user and get JWT token
- POST `/users/signup` - Register a new user
- POST `/users/refresh` - Refresh JWT token using a refresh token
- POST `/users/logout` - Revoke current refresh token and logout
- POST `/users/password/forgot` - Anonymous endpoint that queues a password-reset email (always responds with 202)
- POST `/users/password/reset` - Completes a reset using the emailed token and a new password (anonymous)

### User Management

- GET `/users/me` - Get current user information
- GET `/users` - Get all users (ADMIN only)
- GET `/users/{username}` - Get user by username
- PUT `/users/{username}` - Update user
- DELETE `/users/{username}` - Delete user (ADMIN only)

### Products

- GET `/api/products` - Get all products (authenticated)
- GET `/api/products/{id}` - Get product by ID (authenticated)
- POST `/api/products` - Create new product (ADMIN only)
- PUT `/api/products/{id}` - Update product (ADMIN only)
- DELETE `/api/products/{id}` - Delete product (ADMIN only)

### Shopping Cart

- GET `/api/cart` - Get current user's cart
- POST `/api/cart/items` - Add item to cart
- PUT `/api/cart/items/{productId}` - Update item quantity
- DELETE `/api/cart/items/{productId}` - Remove item from cart
- DELETE `/api/cart` - Clear cart

### Orders

- POST `/api/orders` - Create a new order
- GET `/api/orders` - Get user's orders
- GET `/api/orders/{id}` - Get order by ID
- PUT `/api/orders/{id}/status` - Update order status (ADMIN only)
- POST `/api/orders/{id}/cancel` - Cancel order

### QR Code

- POST `/qr/create` - Generate QR code from text (authenticated)

### Email

- POST `/email` - Send an email (authenticated users only)
- GET `/local/email/outbox` *(local profile only)* - Inspect the in-memory email queue when running without Artemis
- DELETE `/local/email/outbox` *(local profile only)* - Clear the local outbox buffer for a clean test run

#### Local Password Reset Flow

When running with the `local` profile the backend does not connect to Artemis. Instead, every outgoing `EmailDto`
payload is captured by the local outbox endpoint described above so that developers (or the frontend) can retrieve the
latest password-reset link without needing SMTP infrastructure. Each record contains the destination, payload, and a
timestamp. Clearing the outbox before a test run makes it easy to retrieve only the latest link. In Docker/localstack
profiles, reset messages are dispatched through Artemis to the dedicated JMS consumer which forwards them to Mailhog.

Every email message now carries a `template` identifier (e.g., `PASSWORD_RESET_REQUESTED`) and a `properties` map
containing contextual data such as the reset link, expiry window, and username. Downstream consumers can render their
own copy using those properties while the legacy `subject`/`message` fields remain populated for backward compatibility.

## Ollama Integration

The application integrates with Ollama to provide AI text generation and chat capabilities. These features are available
through secure endpoints that require authentication.

### Ollama Endpoints

- POST `/api/ollama/generate` - Generate text using Ollama models
    - Single text generation without conversation history
    - Requires authentication with `ROLE_CLIENT` or `ROLE_ADMIN`
    - Supports Server-Sent Events (SSE) for streaming responses
    - Request body:
      ```json
      {
        "model": "qwen3:0.6b",
        "prompt": "Your prompt here",
        "options": {},
        "think": false
      }
      ```

- POST `/api/ollama/chat` - Chat with Ollama models
    - Supports multi-message conversations with history
    - Client maintains conversation history by sending all previous messages
    - Requires authentication with `ROLE_CLIENT` or `ROLE_ADMIN`
    - Supports Server-Sent Events (SSE) for streaming responses
    - Request body:
      ```json
      {
        "model": "qwen3:0.6b",
        "messages": [
          { "role": "system", "content": "You are a helpful assistant." },
          { "role": "user", "content": "Hello!" },
          { "role": "assistant", "content": "Hi there!" },
          { "role": "user", "content": "How are you?" }
        ],
        "options": {},
        "think": false
      }
      ```

### Request Parameters

Both endpoints support the following parameters:

- `model` (required): The Ollama model to use (e.g., "qwen3:0.6b")
- `options` (optional): Model-specific options (e.g., temperature, max tokens)
- `think` (optional): Set to `true` for 'thinking' models that benefit from reasoning before responding. Defaults to `false`

For the `/generate` endpoint:
- `prompt` (required): The text prompt to generate from

For the `/chat` endpoint:
- `messages` (required): Array of conversation messages with role and content

### Configuration

The Ollama service can be configured in `application.yml`:

```yaml
ollama:
  base-url: http://localhost:11434  # Default Ollama server URL
```

## WebSocket Traffic Monitoring

The application includes a real-time HTTP traffic monitoring system implemented with WebSockets. This feature allows
tracking and visualization of all HTTP requests in the application.

### Features

- Real-time tracking of HTTP requests and responses
- Captures HTTP method, path, status code, response time, and timestamp
- Events are broadcast via WebSocket to connected clients
- Secured access requiring authentication

### Architecture

- Traffic is captured using a servlet filter (`TrafficLoggingFilter`)
- Events are stored in a thread-safe concurrent queue
- A scheduled publisher broadcasts events to WebSocket subscribers
- Uses STOMP protocol over WebSocket for messaging

### WebSocket Endpoints

- WebSocket Connection: `/ws-traffic`
- Subscription Topic: `/topic/traffic`
- Data Format:
  ```json
  {
    "method": "GET",
    "path": "/api/products",
    "status": 200,
    "durationMs": 45,
    "timestamp": "2023-03-22T10:15:30.123Z"
  }
  ```

### Usage

1. Connect to the WebSocket endpoint:
   ```javascript
   const socket = new SockJS('/ws-traffic');
   const stompClient = Stomp.over(socket);
   
   // Include JWT token for authentication
   const headers = {
     'Authorization': 'Bearer ' + jwtToken
   };
   
   stompClient.connect(headers, function(frame) {
     // Subscribe to traffic events
     stompClient.subscribe('/topic/traffic', function(message) {
       const trafficEvent = JSON.parse(message.body);
       console.log('New traffic event:', trafficEvent);
       // Handle event (e.g., update UI)
     });
   });
   ```

2. Traffic events are automatically captured and broadcast as they occur in the application

### Visualization Tool

A simple HTML-based visualization tool is provided to help monitor traffic events in real-time:

1. Run the web server on port 8081 (this is CORS requirement, my frontend uses the same port - see `WebSecurityConfig`).
   Use absolute path

```
jwebserver -p 8081 -d /Users/slawek/IdeaProjects/test-secure-backend
```

2. Go to `http://localhost:8081/traffic-monitor.html`
3. Enter your server URL (default: http://localhost:4001)
4. Paste a valid JWT token (obtained from `/users/signin` endpoint)
5. Click "Connect" to establish the WebSocket connection
6. Watch as HTTP traffic events appear in real-time

### API Endpoints

- GET `/api/traffic/info` - Get WebSocket connection information (authenticated)
    - Returns:
      ```json
      {
        "endpoint": "/ws-traffic",
        "topic": "/topic/traffic",
        "description": "WebSocket endpoint for real-time HTTP traffic events"
      }
      ```

### Test Strategy

The application follows a comprehensive testing strategy focusing on endpoint-level integration tests. Key aspects
include:

1. **Independent Endpoint Testing**
    - Each endpoint is tested in isolation
    - Tests are organized by feature in separate packages
    - All possible HTTP response codes are tested for each endpoint

2. **Test Organization**
    - Tests are ordered by HTTP status code (2xx first, then 4xx, 5xx)
    - Each test class focuses on a single endpoint functionality
    - Example test coverage for an endpoint:
        * 200/201 - Successful operations
        * 400 - Bad Request (invalid input)
        * 401 - Unauthorized (no authentication)
        * 403 - Forbidden (insufficient permissions)
        * 404 - Not Found (resource doesn't exist)

3. **Test Data Generation**
    - Uses factory pattern for test data creation
    - Factories generate random, valid test data using Faker
    - Located in `test/factory` package

4. **Test Structure**
    - Given/When/Then format for clear test organization
    - Descriptive test method names indicating the scenario
    - Comprehensive assertions for response status and body

Example test class organization:

```java
class SomeEndpointTest {
    void shouldReturnSuccessfully(); // 200 OK

    void shouldCreate(); // 201 Created

    void shouldGet400WhenInvalidInput();

    void shouldGet401WhenNoAuthorizationHeader();

    void shouldGet403WhenNotAuthorized();

    void shouldGet404WhenNotFound();
}
```

### AI Debugging Tips

When working with AI assistants, keep in mind:

1. Test failures may be caused by recent changes since git HEAD is kept stable. To see the changes use:
   ```bash
   git --no-pager diff
   ```

2. To run a single test and save the output to the testlogs folder, use JUnit notation:
   ```bash
   mvn test -Dtest=TestClassName#testMethodName > ./testlogs/test-output.log
   ```
   This helps in analyzing test failures by providing detailed logs.

3. The test logs can be read and analyzed by AI to help diagnose issues.

4. When making changes, always verify that all tests pass using:
   ```bash
   mvn test
   ```
