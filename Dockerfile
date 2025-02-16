FROM eclipse-temurin:21-jdk-jammy as build
WORKDIR /app
COPY . .
RUN ./mvnw clean package spring-boot:repackage -DskipTests

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY --from=build /app/target/jwt-auth-service-1.0.0.jar .
EXPOSE 4001
ENTRYPOINT ["java", "-jar", "jwt-auth-service-1.0.0.jar"]