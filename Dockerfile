# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk-jammy@sha256:07a4ee373cd86f337e62cb8149f1e0a9f08aaf3a18c27509a493dda119fb480c AS build
# OpenJDK CVE-2026-91403 mitigation; pinned artifact provenance is in security/java/.
COPY security/java/blocked.certs ${JAVA_HOME}/lib/security/blocked.certs
RUN echo "2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10  ${JAVA_HOME}/lib/security/blocked.certs" | sha256sum --check -
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

FROM eclipse-temurin:25-jre-jammy@sha256:e0d8c47eccd7ec82d635542dfcd1f2b1293add6bdd7ffbeb650f8ec1b1335425
# OpenJDK CVE-2026-91403 mitigation; pinned artifact provenance is in security/java/.
COPY security/java/blocked.certs ${JAVA_HOME}/lib/security/blocked.certs
RUN echo "2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10  ${JAVA_HOME}/lib/security/blocked.certs" | sha256sum --check -
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
