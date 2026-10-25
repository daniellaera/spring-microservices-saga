package com.daniellaera.sagae2e.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * The services' REST APIs, called directly with the headers the gateway would forward after authentication
 * (X-User-Email, and X-User-Role for the admin-only product endpoints).
 */
public final class ShopApi {

    private static final String ADMIN_EMAIL = "admin@e2e.test";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final SagaEnvironment environment;

    public ShopApi(SagaEnvironment environment) {
        this.environment = environment;
    }

    public record OrderLine(String productName, int quantity, BigDecimal price) {}

    public JsonNode createProduct(String name, int quantity, BigDecimal price) {
        return send(HttpRequest.newBuilder(URI.create(environment.inventoryServiceUrl() + "/products"))
                .header("X-User-Email", ADMIN_EMAIL)
                .header("X-User-Role", "ADMIN")
                .header("Content-Type", "application/json")
                .POST(body(Map.of("name", name, "quantity", quantity, "price", price))));
    }

    // By id: not cached, unlike the product list
    public JsonNode product(long id) {
        return send(HttpRequest.newBuilder(URI.create(environment.inventoryServiceUrl() + "/products/" + id)).GET());
    }

    // The cached list, as the shop shows it
    public List<JsonNode> products() {
        JsonNode all = send(HttpRequest.newBuilder(URI.create(environment.inventoryServiceUrl() + "/products")).GET());
        return StreamSupport.stream(all.spliterator(), false).toList();
    }

    /** paymentIntentId may be null: the field is then left out of the request. */
    public JsonNode placeOrder(String customerEmail, List<OrderLine> lines, String paymentIntentId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("items", lines);
        if (paymentIntentId != null) {
            request.put("paymentIntentId", paymentIntentId);
        }
        return send(HttpRequest.newBuilder(URI.create(environment.orderServiceUrl() + "/orders"))
                .header("X-User-Email", customerEmail)
                .header("Content-Type", "application/json")
                .POST(body(request)));
    }

    public JsonNode order(long id, String customerEmail) {
        return send(HttpRequest.newBuilder(URI.create(environment.orderServiceUrl() + "/orders/" + id))
                .header("X-User-Email", customerEmail)
                .GET());
    }

    public List<JsonNode> transactionsOf(long orderId) {
        JsonNode all = send(HttpRequest.newBuilder(URI.create(environment.paymentServiceUrl() + "/transactions")).GET());
        return StreamSupport.stream(all.spliterator(), false)
                .filter(transaction -> transaction.path("orderId").asLong() == orderId)
                .toList();
    }

    // audit-trail-service's saga timeline of the order, oldest first (the gateway only lets an ADMIN through)
    public List<JsonNode> timeline(long orderId) {
        JsonNode all = send(HttpRequest.newBuilder(
                URI.create(environment.auditTrailServiceUrl() + "/audit/orders/" + orderId + "/timeline")).GET());
        return StreamSupport.stream(all.spliterator(), false).toList();
    }

    private static HttpRequest.BodyPublisher body(Object value) {
        return HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(value));
    }

    private JsonNode send(HttpRequest.Builder request) {
        HttpRequest built = request.timeout(Duration.ofSeconds(10)).build();
        try {
            HttpResponse<String> response = http.send(built, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(built.method() + " " + built.uri() + " answered "
                        + response.statusCode() + ": " + response.body());
            }
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException(built.method() + " " + built.uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(built.method() + " " + built.uri() + " interrupted", e);
        }
    }
}
