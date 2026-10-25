# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl notification-service -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY notification-service/target/notification-service.jar notification-service.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "notification-service.jar"]
