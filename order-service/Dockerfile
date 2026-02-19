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
COPY --from=maven-builder /app/order-service/target/order-service.jar order-service.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "order-service.jar"]