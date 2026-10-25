# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl gateway-service -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY gateway-service/target/gateway-service.jar gateway-service.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "gateway-service.jar"]
