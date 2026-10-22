package com.daniellaera.inventoryservice.domain.model;

/**
 * The two lookups by id have always used different messages, and both are part of the HTTP contract.
 */
public class ProductNotFoundException extends RuntimeException {

    private ProductNotFoundException(String message) {
        super(message);
    }

    /** GET /products/{id}. */
    public static ProductNotFoundException withId(long id) {
        return new ProductNotFoundException("Product not found with id: " + id);
    }

    /** PUT /products/{id}/restock. */
    public static ProductNotFoundException forRestock(long id) {
        return new ProductNotFoundException("Product not found: " + id);
    }
}
