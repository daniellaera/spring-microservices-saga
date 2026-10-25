# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl config-server -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY config-server/target/config-server.jar config-server.jar

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "config-server.jar"]
