package com.daniellaera.gatewayservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.util.Date;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {com.daniellaera.gatewayservice.GatewayServiceApplication.class, GatewayServiceITTest.TestConfig.class})
@Import(TestcontainersConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.cloud.config.enabled=false"
})
public class GatewayServiceITTest {

    @Configuration
    static class TestConfig {
        @Bean
        public WebTestClient webTestClient(org.springframework.context.ApplicationContext context) {
            return WebTestClient.bindToApplicationContext(context)
                    .apply(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity())
                    .configureClient()
                    .build();
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    private String secret = "mySecretKey1234567890123456789012345678901234";

    @Test
    void healthCheck_shouldReturnUp() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void protectedRoute_shouldReturnUnauthorized_withoutToken() {
        webTestClient.get().uri("/products")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_shouldReturnNotFound_withValidToken() {
        // NotFound because the target service is not running, but it passed security
        String token = createToken("test@test.com", "USER");

        webTestClient.get().uri("/products")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    private String createToken(String subject, String role) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60))
                .signWith(key)
                .compact();
    }
}
