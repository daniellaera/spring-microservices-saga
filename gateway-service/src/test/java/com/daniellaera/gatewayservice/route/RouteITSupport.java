package com.daniellaera.gatewayservice.route;

import com.daniellaera.gatewayservice.TestcontainersConfiguration;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Runs the real gateway over HTTP, configured as in production: the main application.yml (which the test
 * application.yml otherwise shadows) with its routes and its configserver import, served by a stub config server
 * with the gateway's file from config-server (configs/gateway-service.yml). Subclasses point the routes they test at
 * the same stub, or elsewhere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=file:src/main/resources/application.yml",
        "JWT_SECRET=" + RouteITSupport.SECRET
})
@Import(TestcontainersConfiguration.class)
// each class points its routes at its own stub (a new port per class): never reuse a cached context
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class RouteITSupport {

    static final String SECRET = "mySecretKey1234567890123456789012345678901234";
    private static final String GATEWAY_CONFIG = "../config-server/src/main/resources/configs/gateway-service.yml";

    @RegisterExtension
    static WireMockExtension backend = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @LocalServerPort
    int port;

    // before the context loads: the configserver import is resolved while the environment is prepared
    @BeforeAll
    static void serveTheGatewayConfig() {
        backend.stubFor(WireMock.get(WireMock.urlEqualTo("/gateway-service/default"))
                .willReturn(WireMock.okJson(configServerEnvironment())));
        System.setProperty("CONFIG_SERVER_URI", backend.baseUrl());
    }

    @AfterAll
    static void clearConfigServerUri() {
        System.clearProperty("CONFIG_SERVER_URI");
    }

    // what the native config server answers: the file's properties, flattened
    private static String configServerEnvironment() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(GATEWAY_CONFIG));
        Properties properties = yaml.getObject();
        Map<String, Object> source = new TreeMap<>();
        properties.forEach((key, value) -> source.put(key.toString(), value));
        return JsonMapper.builder().build().writeValueAsString(Map.of(
                "name", "gateway-service",
                "profiles", List.of("default"),
                "propertySources", List.of(Map.of(
                        "name", "classpath:/configs/gateway-service.yml",
                        "source", source))));
    }

    WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    static String token() {
        return Jwts.builder()
                .subject("user@test.com")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }
}
