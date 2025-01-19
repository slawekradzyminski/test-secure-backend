FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY . .
RUN ./mvnw clean package spring-boot:repackage -DskipTests=true
EXPOSE 4001
ENTRYPOINT ["java","-jar","target/jwt-auth-service-1.0.0.jar"]