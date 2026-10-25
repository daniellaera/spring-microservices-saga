package com.daniellaera.sagae2e.steps;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What one scenario created, shared by its step classes (one instance per scenario). Product names and the
 * customer are unique per scenario so scenarios never see each other's stock, orders or audit events.
 */
public class ScenarioContext {

    public record Product(long id, String name, BigDecimal price) {}

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String customerEmail = "customer-" + suffix + "@e2e.test";
    private final Map<String, Product> products = new HashMap<>();
    private int intents;
    private String preparedIntentId;
    private String lastIntentId;
    private final List<Long> orderIds = new ArrayList<>();
    private Long orderId;

    public String uniqueName(String product) {
        return product + " " + suffix;
    }

    /** A payment intent id no other scenario uses: an intent can pay for one order only. */
    public String newPaymentIntentId() {
        return "pi_e2e_" + suffix + "_" + (++intents);
    }

    public void intentPrepared(String intentId) {
        preparedIntentId = intentId;
    }

    public String preparedIntentId() {
        if (preparedIntentId == null) {
            throw new IllegalStateException("No payment intent was prepared in this scenario");
        }
        return preparedIntentId;
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
        orderIds.add(id);
    }

    /** The intent the latest order was paid with. */
    public void orderPaidWith(String intentId) {
        lastIntentId = intentId;
    }

    public String lastIntentId() {
        if (lastIntentId == null) {
            throw new IllegalStateException("No order was paid with an intent in this scenario");
        }
        return lastIntentId;
    }

    public long firstOrderId() {
        if (orderIds.isEmpty()) {
            throw new IllegalStateException("No order was placed in this scenario");
        }
        return orderIds.get(0);
    }

    public long orderId() {
        if (orderId == null) {
            throw new IllegalStateException("No order was placed in this scenario");
        }
        return orderId;
    }
}
