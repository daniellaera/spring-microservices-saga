package com.daniellaera.orderservice.domain.model;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long id) {
        super("Order not found with id: " + id);
    }
}
