package com.daniellaera.inventoryservice.domain.model;

/** An order references a product name that does not exist: the reservation is rolled back and the order REJECTED. */
public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String productName) {
        super("Product not found: " + productName);
    }
}
