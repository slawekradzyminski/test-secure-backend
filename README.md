# Secure Backend API

A secure backend API built with Spring Boot that provides user authentication, authorization, and email functionality.

## Features

- User authentication with JWT tokens
- Role-based authorization (ADMIN and CLIENT roles)
- User management (signup, signin, edit, delete)
- Email sending functionality via ActiveMQ
- Swagger/OpenAPI documentation
- Comprehensive test coverage

## Technologies

- Java 17
- Spring Boot 3.x
- Spring Security with JWT
- Spring Data JPA
- ActiveMQ for email queue
- H2 Database (for development)
- JUnit 5 for testing
- Swagger/OpenAPI for documentation

## Getting Started

### Prerequisites

- Java 17 or higher
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
`http://localhost:8080/swagger-ui/index.html`

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

### Email
- POST `/email` - Send an email (authenticated users only)

## Security

- JWT tokens for authentication
- Password encryption using BCrypt
- Role-based access control
- Cross-Origin Resource Sharing (CORS) configured for localhost:8081

## Error Handling

The API uses standard HTTP status codes:
- 200: Success
- 201: Created
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 422: Unprocessable Entity
- 500: Internal Server Error

## Development

### Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/awesome/testing/
│   │       ├── controller/    # REST controllers
│   │       ├── dto/          # Data Transfer Objects
│   │       ├── model/        # Domain models
│   │       ├── repository/   # Data access layer
│   │       ├── security/     # Security configuration
│   │       └── service/      # Business logic
│   └── resources/
│       └── application.yml   # Application configuration
└── test/
    └── java/
        └── com/awesome/testing/
            └── endpoints/     # Integration tests
```
