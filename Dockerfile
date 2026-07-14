FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN ./mvnw -B -Dmaven.test.skip=true clean package spring-boot:repackage

FROM eclipse-temurin:25-jdk-jammy
WORKDIR /app
COPY --from=build /app/target/jwt-auth-service-1.0.0.jar .
EXPOSE 4001
ENTRYPOINT ["java", "-jar", "jwt-auth-service-1.0.0.jar"]
