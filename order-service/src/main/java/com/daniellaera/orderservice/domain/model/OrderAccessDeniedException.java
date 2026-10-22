package com.daniellaera.orderservice.domain.model;

public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException() {
        super("Access denied");
    }
}
