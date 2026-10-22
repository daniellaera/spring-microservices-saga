package com.daniellaera.orderservice.domain.model;

public class EmptyOrderException extends RuntimeException {

    public EmptyOrderException() {
        super("Order must have at least one item");
    }
}
