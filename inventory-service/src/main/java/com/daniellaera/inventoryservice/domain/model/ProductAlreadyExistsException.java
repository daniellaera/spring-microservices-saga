package com.daniellaera.inventoryservice.domain.model;

public class ProductAlreadyExistsException extends RuntimeException {

    public ProductAlreadyExistsException(String name) {
        super("Product already exists: " + name);
    }
}
