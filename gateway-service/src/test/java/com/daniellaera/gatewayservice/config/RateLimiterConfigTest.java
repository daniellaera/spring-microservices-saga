package com.daniellaera.gatewayservice.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RateLimiterConfigTest {

    private RateLimiterConfig rateLimiterConfig;
    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        rateLimiterConfig = new RateLimiterConfig();
        keyResolver = rateLimiterConfig.userKeyResolver();
    }

    @Test
    public void keyResolver_shouldReturnUserKey_whenAuthHeaderIsPresent() {
        String token = "Bearer my-secret-token-123456";
        String expectedKey = token.substring(7, 20);
        
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header(HttpHeaders.AUTHORIZATION, token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext(expectedKey)
                .verifyComplete();
    }

    @Test
    public void keyResolver_shouldReturnIpAddress_whenAuthHeaderIsMissing() {
        String ip = "192.168.1.1";
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress(ip, 8080))
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext(ip)
                .verifyComplete();
    }

    @Test
    public void keyResolver_shouldReturnAnonymous_whenAuthHeaderAndRemoteAddressAreMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .build();
        // MockServerHttpRequest by default might not have remote address? 
        // Let's check RateLimiterConfig logic: 
        // exchange.getRequest().getRemoteAddress() != null
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<String> keyMono = keyResolver.resolve(exchange);

        StepVerifier.create(keyMono)
                .expectNext("anonymous")
                .verifyComplete();
    }
}
