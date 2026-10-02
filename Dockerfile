# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk-jammy@sha256:89565961a318534f01c971c7b1d030e60713c66995b887c94010cef938dbc53e AS build
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -Dmaven.test.skip=true clean package \
    && cp target/jwt-auth-service-*.jar app.jar

FROM eclipse-temurin:25-jre-jammy@sha256:10c251954d0bfe1a59ba93505f8c628d755919412400aa98685764c9353605d6
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/logs \
    && chown app:app /app/logs \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build --chown=app:app /app/app.jar .
USER app
EXPOSE 4001
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD curl --fail --silent --show-error http://localhost:4001/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
