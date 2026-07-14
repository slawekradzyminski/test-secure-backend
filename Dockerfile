# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk-jammy AS build
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
    ./mvnw -B -Dmaven.test.skip=true clean package

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/logs \
    && chown app:app /app/logs \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build --chown=app:app /app/target/jwt-auth-service-1.0.0.jar .
USER app
EXPOSE 4001
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD curl --fail --silent --show-error http://localhost:4001/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "jwt-auth-service-1.0.0.jar"]
