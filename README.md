## Direct run

```commandline
./mvnw clean package spring-boot:repackage
java -jar target/jwt-auth-service-1.0.0.jar
```

## Docker local run

```commandline
docker build --tag=jwt-auth-service:latest .
docker run -p4000:4000 jwt-auth-service:latest
```

## Docker remote run (warning: may be outdated)

```commandline
docker run -p4000:4000 slawekradzyminski/jwt-auth-service:latest
```

## Verification

[Swagger](http://localhost:4000/swagger-ui.html)

[Database](http://localhost:4000/h2-console) login as root/root

## Existing users

- client/client (CLIENT role)
- admin/admin (ADMIN role)
