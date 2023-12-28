## Direct run

```commandline
./mvnw clean package spring-boot:repackage
java -Dspring.profiles.active=dev -jar target/jwt-auth-service-1.0.0.jar
```

## Docker local run

```commandline
docker-compose up
```

## Docker remote run (warning: may be outdated)

```commandline
docker run -p4001:4001 slawekradzyminski/backend:1.2
```

## Publish image

[https://docs.docker.com/docker-hub/repos/](https://docs.docker.com/docker-hub/repos/)

## Verification

[Swagger](http://localhost:4001/swagger-ui.html)

[Database](http://localhost:4001/h2-console) login as root/root

## Existing users

- client/client (CLIENT role)
- admin/admin (ADMIN role)
