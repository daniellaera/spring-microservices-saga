package com.daniellaera.frontendservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GatewayClient {

    private final RestTemplate restTemplate;
    private final String gatewayUrl;

    public GatewayClient(@Value("${gateway.url}") String gatewayUrl) {
        this.restTemplate = new RestTemplate();
        this.gatewayUrl = gatewayUrl;
    }

    public String login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                gatewayUrl + "/auth/login", entity, String.class);
        return response.getBody();
    }

    public String getOrders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                gatewayUrl + "/orders", HttpMethod.GET, entity, String.class);
        return response.getBody();
    }

    public String getProducts(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                gatewayUrl + "/products", HttpMethod.GET, entity, String.class);
        return response.getBody();
    }

    public String createOrder(String token, String productName, int quantity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String body = "{\"productName\":\"" + productName + "\",\"quantity\":" + quantity + "}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                gatewayUrl + "/orders", entity, String.class);
        return response.getBody();
    }
}
