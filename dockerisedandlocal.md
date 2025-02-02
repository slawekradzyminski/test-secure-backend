# Implementation Plan for Local and Dockerized Configurations

## Current State Analysis

The application currently has:
- A test profile with in-memory ActiveMQ and H2 database
- A default profile using dockerized ActiveMQ and in-memory H2 database
- Integration tests running with test profile
- Existing docker-compose.yml with ActiveMQ configuration

## Implementation Plan

### 1. Configuration Changes

#### 1.1 Create Profile-Specific Configuration Files
- Create `application-local.yml`
  - Copy embedded ActiveMQ configuration from test profile
  - Keep H2 database configuration
  - Set server port to 4001
- Update `application.yml` to use docker profile by default
  - Move common configuration here
  - Keep H2 database as default for simplicity
  - Configure external ActiveMQ connection

#### 1.2 Update Docker Compose
- Add PostgreSQL service to existing docker-compose.yml:
```yaml
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: root
      POSTGRES_PASSWORD: root
    ports:
      - "5432:5432"
    networks:
      - my-private-ntwk
```

### 2. Code Changes

#### 2.1 Create Local Profile Configuration
- Create `LocalConfig.java`
  - Copy embedded ActiveMQ configuration from `TestConfig.java`
  - Add `@Profile("local")` annotation

#### 2.2 Update Transaction Configuration
- Update `TransactionConfig.java` to be profile-aware:
  - Add `@Profile("!local")` for docker profile beans
  - Keep current external ActiveMQ configuration

### 3. Testing Strategy

#### 3.1 Integration Tests
- Keep using test profile with in-memory services
- No changes needed to test configuration

#### 3.2 End-to-End Tests
- Create E2E test suite using docker-compose
- Add docker-compose-test.yml for E2E testing

### 4. Documentation Updates

#### 4.1 Update README.md
- Add profile-specific run instructions
- Document docker-compose usage
- Add troubleshooting section

## Implementation Steps

1. **Phase 1: Local Profile Setup**
   - Create `application-local.yml` based on test profile
   - Implement `LocalConfig.java`
   - Test local profile functionality

2. **Phase 2: Docker Integration**
   - Update docker-compose.yml with PostgreSQL
   - Update `TransactionConfig.java`
   - Test docker profile functionality

3. **Phase 3: Testing and Documentation**
   - Create E2E test configuration
   - Update documentation

## Technical Details

### Profile-Specific Beans

```java
@Configuration
@Profile("local")
public class LocalConfig {
    // Copy configuration from TestConfig
    // Only embedded ActiveMQ configuration needed
}
```

### Running the Application

Local Profile (in-memory everything):
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Docker Profile (with external services):
```bash
docker-compose up
```

### Testing

```bash
# Run unit and integration tests (uses test profile)
mvn test

# Run E2E tests with docker compose
./run-e2e-tests.sh
```

## Docker Compose Structure

```yaml
services:
  backend:
    image: slawekradzyminski/backend:2.3
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/testdb
    depends_on:
      - postgres
      - activemq

  activemq:
    image: apache/activemq-artemis:2.31.2
    # ... existing configuration ...

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: root
      POSTGRES_PASSWORD: root

networks:
  my-private-ntwk:
    driver: bridge
```
