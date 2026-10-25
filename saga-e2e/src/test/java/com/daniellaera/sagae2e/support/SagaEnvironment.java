package com.daniellaera.sagae2e.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * order-service, inventory-service, payment-service and audit-trail-service (for the saga timeline), each run from its
 * own packaged jar, with Postgres, Kafka,
 * Redis and WireMock (as Stripe and as config server) on one Docker network. Started once for the whole suite;
 * Testcontainers removes the containers, network and built images when the JVM exits.
 */
public final class SagaEnvironment {

    private static final Logger log = LoggerFactory.getLogger(SagaEnvironment.class);

    // Same images as the services' own integration tests
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";
    private static final String KAFKA_IMAGE = "apache/kafka:4.2.0";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String JRE_IMAGE = "eclipse-temurin:21-jre";

    // Dummy credentials: nothing here reaches a real system
    private static final String DB_USER = "testuser";
    private static final String DB_PASSWORD = "testpass";
    private static final String STRIPE_SECRET_KEY = "sk_test_e2e_dummy";
    private static final String STRIPE_PUBLISHABLE_KEY = "pk_test_e2e_dummy";

    private static final String KAFKA_INTERNAL = "kafka:19092";
    private static final String WIREMOCK_INTERNAL = "http://wiremock:8080";
    private static final Duration SERVICE_STARTUP_TIMEOUT = Duration.ofMinutes(3);

    private static SagaEnvironment instance;

    private final Path repoRoot = Path.of(System.getProperty("saga.e2e.repo-root", "..")).toAbsolutePath().normalize();
    private final Network network = Network.newNetwork();

    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD)
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/init-databases.sql"),
                    "/docker-entrypoint-initdb.d/init-databases.sql");

    private final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withListener(KAFKA_INTERNAL);

    private final GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE)
            .withNetwork(network)
            .withNetworkAliases("redis")
            .withExposedPorts(6379);

    private final GenericContainer<?> wiremock = new GenericContainer<>(
            "wiremock/wiremock:" + System.getProperty("saga.e2e.wiremock-version", "3.13.2"))
            .withNetwork(network)
            .withNetworkAliases("wiremock")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/health").forPort(8080).forStatusCode(200));

    private final Map<String, GenericContainer<?>> services = new LinkedHashMap<>();
    private WireMockStubs stubs;
    private KafkaTraffic kafkaTraffic;

    private SagaEnvironment() {
    }

    public static synchronized SagaEnvironment get() {
        if (instance == null) {
            SagaEnvironment environment = new SagaEnvironment();
            environment.start();
            instance = environment;
        }
        return instance;
    }

    private void start() {
        long started = System.nanoTime();
        Startables.deepStart(postgres, kafka, redis, wiremock).join();
        stubs = WireMockStubs.install(wiremock.getHost(), wiremock.getMappedPort(8080));

        services.put("order-service", service("order-service", 8081, Map.of()));
        services.put("inventory-service", service("inventory-service", 8082, Map.of("REDIS_HOST", "redis")));
        services.put("payment-service", service("payment-service", 8083, Map.of()));
        services.put("audit-trail-service", service("audit-trail-service", 8087, Map.of()));
        Startables.deepStart(services.values()).join();

        kafkaTraffic = new KafkaTraffic(kafka.getBootstrapServers());
        log.info("=== Saga environment up in {}s", Duration.ofNanos(System.nanoTime() - started).toSeconds());
    }

    /**
     * The service jar in a minimal JRE image, configured the way docker-compose.yml and config-server do in prod:
     * the same environment variables, and the properties of config-server/src/main/resources/configs/<service>.yml
     * (passed as SPRING_APPLICATION_JSON) with dummy secrets. The OTLP settings of configs/application.yml are left
     * out: there is no collector. The config client cannot be switched off (the services' configserver: import is
     * not optional), so CONFIG_SERVER_URI points at WireMock, which serves an empty environment.
     */
    private GenericContainer<?> service(String name, int port, Map<String, String> extraEnv) {
        Path jar = repoRoot.resolve(name + "/target/" + name + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(jar + " not found: package the service first (mvn -Pe2e -pl saga-e2e -am verify)");
        }
        String jarInContext = name + "/target/" + name + ".jar";
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withFileFromPath(jarInContext, jar)
                .withDockerfileFromBuilder(dockerfile -> dockerfile
                        .from(JRE_IMAGE)
                        .workDir("/app")
                        .copy(jarInContext, "app.jar")
                        .entryPoint("java", "-jar", "app.jar")
                        .build());

        Map<String, String> env = new LinkedHashMap<>();
        env.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA_INTERNAL);
        env.put("DB_HOST", "postgres");
        env.put("DB_USER", DB_USER);
        env.put("DB_PASSWORD", DB_PASSWORD);
        env.put("CONFIG_SERVER_URI", WIREMOCK_INTERNAL);
        env.put("JAVA_TOOL_OPTIONS", "-Xms64m -Xmx256m");
        // no collector: don't push metrics to the default OTLP endpoint
        env.put("MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED", "false");
        env.put("SPRING_APPLICATION_JSON", JsonMapper.builder().build().writeValueAsString(configServerProperties(name)));
        env.putAll(extraEnv);

        return new GenericContainer<>(image)
                .withNetwork(network)
                .withNetworkAliases(name)
                .withExposedPorts(port)
                .withEnv(env)
                .waitingFor(Wait.forHttp("/actuator/health").forPort(port).forStatusCode(200)
                        .withStartupTimeout(SERVICE_STARTUP_TIMEOUT));
    }

    // Mirrors config-server/src/main/resources/configs/<service>.yml
    private static Map<String, Object> configServerProperties(String service) {
        Map<String, Object> spring = Map.of(
                "datasource", Map.of("username", DB_USER, "password", DB_PASSWORD),
                "kafka", Map.of("producer", Map.of("properties", Map.of(
                        "max.block.ms", 1000,
                        "request.timeout.ms", 1000,
                        "delivery.timeout.ms", 2000))));
        if (!service.equals("payment-service")) {
            return Map.of("spring", spring);
        }
        return Map.of(
                "spring", spring,
                "stripe", Map.of(
                        "secret-key", STRIPE_SECRET_KEY,
                        "publishable-key", STRIPE_PUBLISHABLE_KEY,
                        // e2e only: the Stripe SDK talks to WireMock (never set in a prod config)
                        "api-base", WIREMOCK_INTERNAL));
    }

    public String orderServiceUrl() {
        return baseUrl("order-service", 8081);
    }

    public String inventoryServiceUrl() {
        return baseUrl("inventory-service", 8082);
    }

    public String paymentServiceUrl() {
        return baseUrl("payment-service", 8083);
    }

    public String auditTrailServiceUrl() {
        return baseUrl("audit-trail-service", 8087);
    }

    private String baseUrl(String service, int port) {
        GenericContainer<?> container = services.get(service);
        return "http://" + container.getHost() + ":" + container.getMappedPort(port);
    }

    public WireMockStubs stubs() {
        return stubs;
    }

    public KafkaTraffic kafka() {
        return kafkaTraffic;
    }

    public List<String> serviceNames() {
        return List.copyOf(services.keySet());
    }

    public String logsOf(String service) {
        return services.get(service).getLogs();
    }
}
