FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY src src
COPY pom.xml .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/notifications-microservice-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
