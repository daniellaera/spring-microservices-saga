# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl inventory-service -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY inventory-service/target/inventory-service.jar inventory-service.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "inventory-service.jar"]
