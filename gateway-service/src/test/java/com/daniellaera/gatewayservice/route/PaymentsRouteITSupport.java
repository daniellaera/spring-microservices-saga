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
import org.springframework.http.MediaType;
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
 * with the gateway's file from config-server (configs/gateway-service.yml). The same stub plays payment-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=file:src/main/resources/application.yml",
        "JWT_SECRET=" + PaymentsRouteITSupport.SECRET
})
@Import(TestcontainersConfiguration.class)
abstract class PaymentsRouteITSupport {

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
    static void serveTheGatewayConfigAndPointTheGatewayAtTheStub() {
        backend.stubFor(WireMock.get(WireMock.urlEqualTo("/gateway-service/default"))
                .willReturn(WireMock.okJson(configServerEnvironment())));
        System.setProperty("CONFIG_SERVER_URI", backend.baseUrl());
        System.setProperty("PAYMENT_SERVICE_URI", backend.baseUrl());
    }

    @AfterAll
    static void clearUris() {
        System.clearProperty("CONFIG_SERVER_URI");
        System.clearProperty("PAYMENT_SERVICE_URI");
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

    void paymentServiceAnswersCreateIntentAfter(int delayMillis) {
        backend.stubFor(WireMock.post(WireMock.urlEqualTo("/payments/create-intent"))
                .willReturn(WireMock.okJson("""
                        {"clientSecret":"pi_1_secret_1","paymentIntentId":"pi_1"}""")
                        .withFixedDelay(delayMillis)));
    }

    WebTestClient.ResponseSpec createIntent() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build()
                .post().uri("/payments/create-intent")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount":129999,"currency":"eur","productName":"MacBook Pro"}""")
                .exchange();
    }

    private static String token() {
        return Jwts.builder()
                .subject("user@test.com")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }
}
