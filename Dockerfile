FROM eclipse-temurin:21-jdk
COPY . ./
RUN chmod u+x ./mvnw
RUN ./mvnw clean package spring-boot:repackage -Pdev -DskipTests=true
ENTRYPOINT ["java","-Dspring.profiles.active=dev","-jar","target/jwt-auth-service-1.0.0.jar"]
