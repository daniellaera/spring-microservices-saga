# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl payment-service -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY payment-service/target/payment-service.jar payment-service.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "payment-service.jar"]
