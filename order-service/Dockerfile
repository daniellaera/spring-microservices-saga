# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl order-service -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY order-service/target/order-service.jar order-service.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "order-service.jar"]
