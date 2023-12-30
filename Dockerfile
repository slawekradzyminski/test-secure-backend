FROM eclipse-temurin:21-jdk
COPY . ./
RUN chmod u+x ./mvnw
RUN ./mvnw clean package spring-boot:repackage -Pprod -DskipTests=true
ENTRYPOINT ["java","-Dspring.profiles.active=prod","-jar","target/jwt-auth-service-1.0.0.jar"]
