# Step 1: Build all modules
FROM maven:3.9.9-eclipse-temurin-21-alpine AS maven-builder

WORKDIR /app
COPY . .

# Build everything
RUN mvn clean package -DskipTests

# Step 2: Runtime
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

COPY --from=maven-builder /app/config-server/target/config-server.jar config-server.jar

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "config-server.jar"]