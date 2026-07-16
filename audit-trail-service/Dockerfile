# Step 1: Build all modules
FROM maven:3.9.9-eclipse-temurin-21-alpine AS maven-builder

WORKDIR /app
COPY . .

# Build everything
RUN mvn clean package -DskipTests

# Step 2: Runtime for each service
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# Copy the JAR of THIS service
COPY --from=maven-builder /app/audit-trail-service/target/audit-trail-service.jar audit-trail-service.jar

EXPOSE 8087
ENTRYPOINT ["java", "-jar", "audit-trail-service.jar"]
