package com.daniellaera.gatewayservice.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtWebFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private WebFilterChain chain;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @InjectMocks
    private JwtWebFilter jwtWebFilter;

    @BeforeEach
    void setUp() {
        when(exchange.getRequest()).thenReturn(request);
    }

    @Test
    void filter_shouldPassThrough_whenPathIsAuth() {
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse(org.springframework.web.util.pattern.PathPatternParser.defaultInstance.parse("/auth/login").getPatternString(), "/auth/login"));
        when(request.getMethod()).thenReturn(org.springframework.http.HttpMethod.POST);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(jwtWebFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void filter_shouldReturnUnauthorized_whenNoAuthHeader() {
        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse(org.springframework.web.util.pattern.PathPatternParser.defaultInstance.parse("/products").getPatternString(), "/products"));
        when(request.getMethod()).thenReturn(org.springframework.http.HttpMethod.GET);
        when(request.getHeaders()).thenReturn(HttpHeaders.EMPTY);
        when(exchange.getResponse()).thenReturn(response);
        when(response.setStatusCode(HttpStatus.UNAUTHORIZED)).thenReturn(true);
        when(response.setComplete()).thenReturn(Mono.empty());

        StepVerifier.create(jwtWebFilter.filter(exchange, chain))
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(response).setComplete();
    }

    @Test
    void filter_shouldPass_whenTokenIsValid() {
        String token = "valid-token";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse(org.springframework.web.util.pattern.PathPatternParser.defaultInstance.parse("/products").getPatternString(), "/products"));
        when(request.getMethod()).thenReturn(org.springframework.http.HttpMethod.GET);
        when(request.getHeaders()).thenReturn(headers);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractEmail(token)).thenReturn("user@test.com");
        when(jwtUtil.extractRole(token)).thenReturn("USER");

        ServerHttpRequest.Builder builder = mock(ServerHttpRequest.Builder.class);
        when(request.mutate()).thenReturn(builder);
        when(builder.header(any(), any())).thenReturn(builder);
        when(builder.build()).thenReturn(request);
        
        ServerWebExchange.Builder exchangeBuilder = mock(ServerWebExchange.Builder.class);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(any(ServerHttpRequest.class))).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(exchange);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(jwtWebFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void filter_shouldReturnForbidden_whenUserAccessesAdminPath() {
        String token = "valid-token";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        when(request.getPath()).thenReturn(org.springframework.http.server.RequestPath.parse(org.springframework.web.util.pattern.PathPatternParser.defaultInstance.parse("/products").getPatternString(), "/products"));
        when(request.getMethod()).thenReturn(org.springframework.http.HttpMethod.POST);
        when(request.getHeaders()).thenReturn(headers);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractEmail(token)).thenReturn("user@test.com");
        when(jwtUtil.extractRole(token)).thenReturn("USER");
        when(exchange.getResponse()).thenReturn(response);
        when(response.setStatusCode(HttpStatus.FORBIDDEN)).thenReturn(true);
        when(response.setComplete()).thenReturn(Mono.empty());

        StepVerifier.create(jwtWebFilter.filter(exchange, chain))
                .verifyComplete();

        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verify(response).setComplete();
    }
}
