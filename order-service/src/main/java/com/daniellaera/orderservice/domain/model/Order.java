package com.daniellaera.orderservice.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class Order {

    private final Long id;
    private final String userEmail;
    private final String paymentIntentId;
    private final String productName;
    private final int quantity;
    private final BigDecimal price;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private final LocalDateTime createdAt;
    private OrderStatus status;

    private Order(Long id, String userEmail, String paymentIntentId, OrderStatus status, BigDecimal totalAmount,
                  LocalDateTime createdAt, String productName, int quantity, BigDecimal price, List<OrderItem> items) {
        this.id = id;
        this.userEmail = userEmail;
        this.paymentIntentId = paymentIntentId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public static Order place(String userEmail, String paymentIntentId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new EmptyOrderException();
        }
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        OrderItem first = items.get(0);
        return new Order(null, userEmail, paymentIntentId, OrderStatus.PENDING, totalAmount, null,
                first.productName(), first.quantity(), first.price(), items);
    }

    /**
     * Rebuilds an order from persistence. Only the persistence mapper should call this.
     */
    public static Order restore(Long id, String userEmail, String paymentIntentId, OrderStatus status,
                                BigDecimal totalAmount, LocalDateTime createdAt,
                                String productName, int quantity, BigDecimal price, List<OrderItem> items) {
        return new Order(id, userEmail, paymentIntentId, status, totalAmount, createdAt,
                productName, quantity, price, items);
    }

    // No transition guard: a confirmed order can still be cancelled and vice versa (current behaviour)
    public void applyPaymentResult(boolean succeeded) {
        status = succeeded ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED;
    }

    public void ensureReadableBy(String requesterEmail, boolean admin) {
        // Throws NullPointerException for a non-admin when the order has no email (current behaviour)
        if (!admin && !userEmail.equals(requesterEmail)) {
            throw new OrderAccessDeniedException();
        }
    }

    public Long id() {
        return id;
    }

    public String userEmail() {
        return userEmail;
    }

    public String paymentIntentId() {
        return paymentIntentId;
    }

    public OrderStatus status() {
        return status;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public List<OrderItem> items() {
        return items;
    }

    /**
     * Persisted legacy single-item snapshot, the only item data for orders created before V6 (no order_items rows).
     * For orders placed since, it is the first item.
     */
    public String productName() {
        return productName;
    }

    /**
     * Persisted legacy single-item snapshot, the only item data for orders created before V6 (no order_items rows).
     * For orders placed since, it is the first item.
     */
    public int quantity() {
        return quantity;
    }

    /**
     * Persisted legacy single-item snapshot, the only item data for orders created before V6 (no order_items rows).
     * For orders placed since, it is the first item.
     */
    public BigDecimal price() {
        return price;
    }
}
