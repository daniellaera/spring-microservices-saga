# Runtime image only. The jar is built beforehand, from the repo root:
#   mvn -pl cart-service -am package -DskipTests
# (main.yml builds every changed service in one Maven run before building the images.)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY cart-service/target/cart-service.jar cart-service.jar

EXPOSE 8086
ENTRYPOINT ["java", "-jar", "cart-service.jar"]
