FROM eclipse-temurin:17.0.6_10-jdk-focal
COPY . ./
RUN chmod u+x ./mvnw
RUN ./mvnw clean package spring-boot:repackage -DskipTests=true
ENTRYPOINT ["java","-jar","target/jwt-auth-service-1.0.0.jar"]