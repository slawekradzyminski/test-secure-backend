FROM maven:3.9.6-eclipse-temurin-17-focal
COPY . ./
RUN mvn clean package spring-boot:repackage -DskipTests=true
ENTRYPOINT ["java","-jar","target/jwt-auth-service-1.0.0.jar"]