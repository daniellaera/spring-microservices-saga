package com.daniellaera.configserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.client.ConfigClientFailFastException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The config server as deployed (native profile, configs/ from the classpath): every client gets its own file, an
 * application without one gets a 404 instead of the shared application.yml alone, and a client whose file is
 * missing fails its startup on it. The health check still answers UP.
 */
@SpringBootTest(classes = ConfigServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationFileRequiredITTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    // the client's application: no beans, only the configuration phase runs (not a @Configuration, which would
    // become this test's own)
    static class NoBeans {
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"audit-trail-service", "auth-service", "cart-service", "gateway-service",
            "inventory-service", "notification-service", "order-service", "payment-service"})
    void everyClient_getsItsOwnFile(String module) throws Exception {
        // the name the client asks for, from its own application.yml(.yaml): a rename on either side fails here
        String name = applicationYml(module).getProperty("spring.application.name");
        HttpResponse<String> response = get("/" + name + "/default");

        // 200 because configs/<name>.yml exists; one holding only comments (cart-service's) adds no property source
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"" + name + "\"").contains("configs/application.yml");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/payment-servic/default", "/payment-servic/default/main", "/payment-servic-default.yml",
            "/payment-servic-default.properties", "/main/payment-servic-default.json", "/payment-service,payment-servic/default"})
    void anApplicationWithoutItsOwnFile_isNotFound(String path) throws Exception {
        assertThat(get(path).statusCode()).isEqualTo(404);
    }

    @Test
    void theSharedFile_isStillServedForItself() throws Exception {
        assertThat(get("/application/default").statusCode()).isEqualTo(200);
    }

    @Test
    void health_isUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void aClientWithItsFile_startsOnIt() {
        try (ConfigurableApplicationContext context = startClient("payment-service", "payment-service")) {
            assertThat(StreamSupport.stream(context.getEnvironment().getPropertySources().spliterator(), false)
                    .map(PropertySource::getName))
                    .anyMatch(name -> name.contains("configs/payment-service.yml"));
        }
    }

    @Test
    void aClientWhoseFileIsMissing_failsItsStartup() {
        // payment-service's own application.yml under a name config-server has no file for (a renamed file)
        assertThatThrownBy(() -> startClient("payment-service", "payment-servic").close())
                .isInstanceOf(ConfigClientFailFastException.class)
                .hasMessageContaining("None of labels");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Properties applicationYml(String module) {
        Path yml = Path.of("../" + module + "/src/main/resources/application.yml");
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Files.exists(yml) ? yml : yml.resolveSibling("application.yaml")));
        return yaml.getObject();
    }

    // As in ClientConfigImportTest: the client's own application.yml, the backoff shortened
    private ConfigurableApplicationContext startClient(String module, String applicationName) {
        Path yml = Path.of("../" + module + "/src/main/resources/application.yml");
        assertThat(Files.exists(yml)).isTrue();
        return new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .run("--spring.cloud.config.enabled=true",
                        "--spring.config.location=file:" + yml,
                        "--spring.application.name=" + applicationName,
                        "--CONFIG_SERVER_URI=http://localhost:" + port,
                        "--spring.cloud.config.retry.initial-interval=20",
                        "--spring.cloud.config.retry.max-interval=50");
    }
}
