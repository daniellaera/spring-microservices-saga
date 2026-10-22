package com.daniellaera.sagae2e.steps;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What one scenario created, shared by its step classes (one instance per scenario). Product names and the
 * customer are unique per scenario so scenarios never see each other's stock, orders or audit events.
 */
public class ScenarioContext {

    public record Product(long id, String name, BigDecimal price) {}

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String customerEmail = "customer-" + suffix + "@e2e.test";
    private final Map<String, Product> products = new HashMap<>();
    private Long orderId;

    public String uniqueName(String product) {
        return product + " " + suffix;
    }

    public String customerEmail() {
        return customerEmail;
    }

    public void addProduct(String product, Product created) {
        products.put(product, created);
    }

    public Product product(String product) {
        Product found = products.get(product);
        if (found == null) {
            throw new IllegalStateException("No product \"" + product + "\" was set up in this scenario");
        }
        return found;
    }

    public void orderPlaced(long id) {
        orderId = id;
    }

    public long orderId() {
        if (orderId == null) {
            throw new IllegalStateException("No order was placed in this scenario");
        }
        return orderId;
    }
}
