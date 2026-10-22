package com.daniellaera.inventoryservice.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A product and its stock. The arithmetic is the plain addition and subtraction the service has always done:
 * there is no guard against negative stock or overflow (current behaviour).
 */
public class Product {

    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final LocalDateTime createdAt;
    private int quantity;

    private Product(Long id, String name, int quantity, BigDecimal price, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.createdAt = createdAt;
    }

    /** A product that has never been persisted: no id, no createdAt. */
    public static Product create(String name, int quantity, BigDecimal price) {
        return new Product(null, name, quantity, price, null);
    }

    /**
     * Rebuilds a product from persistence. Only the persistence mapper (and test fakes) should call this.
     */
    public static Product restore(Long id, String name, int quantity, BigDecimal price, LocalDateTime createdAt) {
        return new Product(id, name, quantity, price, createdAt);
    }

    public boolean canReserve(int requested) {
        return quantity >= requested;
    }

    public void deduct(int requested) {
        quantity -= requested;
    }

    public void release(int reserved) {
        quantity += reserved;
    }

    public void restock(int added) {
        quantity += added;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int quantity() {
        return quantity;
    }

    public BigDecimal price() {
        return price;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
