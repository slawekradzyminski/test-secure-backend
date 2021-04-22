```commandline
./mvnw clean package spring-boot:repackage
java -jar target/jwt-auth-service-1.0.0.jar
```

## Verification

[Swagger](http://localhost:8080/swagger-ui.html)

[Database](http://localhost:8080/h2-console) login as root/root

## Existing users

- client/client (CLIENT role)
- admin/admin (ADMIN role)