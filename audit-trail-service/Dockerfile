# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl audit-trail-service -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY audit-trail-service/target/audit-trail-service.jar audit-trail-service.jar

EXPOSE 8087
ENTRYPOINT ["java", "-jar", "audit-trail-service.jar"]
