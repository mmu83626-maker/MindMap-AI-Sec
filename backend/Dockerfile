FROM maven:3.8-openjdk-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:resolve
COPY src/ src/
RUN mvn clean package -DskipTests

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
