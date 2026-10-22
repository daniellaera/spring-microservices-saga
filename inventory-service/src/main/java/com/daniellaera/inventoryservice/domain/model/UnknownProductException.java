package com.daniellaera.inventoryservice.domain.model;

/** An order references a product name that does not exist: the reservation is rolled back and nothing is published. */
public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String productName) {
        super("Product not found: " + productName);
    }
}
