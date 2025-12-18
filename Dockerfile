FROM eclipse-temurin:25-jdk-jammy as build
WORKDIR /app

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
