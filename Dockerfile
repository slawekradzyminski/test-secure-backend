FROM eclipse-temurin:17-jre-focal
COPY target/jwt-auth-service-1.0.0.jar jwt-auth-service-1.0.0.jar
ENTRYPOINT ["java","-jar","/jwt-auth-service-1.0.0.jar"]