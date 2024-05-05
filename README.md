## Direct run

Requires [Java 21](https://docs.papermc.io/misc/java-install)

```commandline
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Docker local run

Requires [docker](https://docs.docker.com/engine/install/)
and [docker-compose](https://docs.docker.com/compose/install/) installed

```commandline
docker-compose up --build
```

## Docker remote run (warning: may be outdated)

```commandline
docker run -p8081:8081 slawekradzyminski/backend:1.2
```

## Deploy to Google Cloud Engine

```commandline
./mvnw -DskipTests package appengine:deploy
```

## Publish image

[https://docs.docker.com/docker-hub/repos/](https://docs.docker.com/docker-hub/repos/)

## Verification

[Swagger](http://localhost:8081/swagger-ui/index.html)

[Database](http://localhost:8081/h2-console) login as root/root

## Existing users

- client/client (CLIENT role)
- admin/admin (ADMIN role)
- doctor/doctor (DOCTOR role)
