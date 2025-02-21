# Secure Backend Application

This is a secure backend application built with Spring Boot, featuring JWT authentication, PostgreSQL database, and ActiveMQ messaging.

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
- Swagger UI: http://localhost:4001/swagger-ui.html
- OpenAPI JSON: http://localhost:4001/v3/api-docs

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

## Features

- User authentication with JWT tokens
- Role-based authorization (ADMIN and CLIENT roles)
- User management (signup, signin, edit, delete)
- Email sending functionality via ActiveMQ
- Ollama integration for AI text generation
- Product management
- Shopping cart functionality
- Order management
- Swagger/OpenAPI documentation
- Comprehensive test coverage

## Getting Started

### Prerequisites

- Java 21 or higher
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

## API Documentation

Once the application is running, you can access the Swagger UI at:
`http://localhost:4001/swagger-ui/index.html`

## API Endpoints

### Authentication
- POST `/users/signin` - Authenticate user and get JWT token
- POST `/users/signup` - Register a new user
- GET `/users/refresh` - Refresh JWT token

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

## Ollama Integration

The application integrates with Ollama to provide AI text generation capabilities. This feature is available through a secure endpoint that requires authentication.

### Ollama Endpoints

- POST `/api/ollama/generate` - Generate text using Ollama models
  - Requires authentication with `ROLE_CLIENT` or `ROLE_ADMIN`
  - Supports Server-Sent Events (SSE) for streaming responses
  - Request body:
    ```json
    {
      "model": "gemma:2b",
      "prompt": "Your prompt here",
      "options": {}
    }
    ```

### Configuration

The Ollama service can be configured in `application.yml`:
```yaml
ollama:
  base-url: http://localhost:11434  # Default Ollama server URL
```

### Test Strategy

The application follows a comprehensive testing strategy focusing on endpoint-level integration tests. Key aspects include:

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
    void shouldReturnSuccessfully() // 200 OK
    void shouldCreate() // 201 Created
    void shouldGet400WhenInvalidInput()
    void shouldGet401WhenNoAuthorizationHeader()
    void shouldGet403WhenNotAuthorized()
    void shouldGet404WhenNotFound()
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
