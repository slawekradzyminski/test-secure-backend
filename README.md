## Direct run

Requires [Java 21](https://docs.papermc.io/misc/java-install)

```commandline
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Docker local run

Requires [docker](https://docs.docker.com/engine/install/)
and [docker-compose](https://docs.docker.com/compose/install/) installed

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
