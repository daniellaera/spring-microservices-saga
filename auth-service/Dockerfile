# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl auth-service -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY auth-service/target/auth-service.jar auth-service.jar

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "auth-service.jar"]
