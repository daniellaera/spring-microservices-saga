package com.daniellaera.orderservice.dto;

public record PaymentEvent(Long orderId, String productName, Integer quantity, String status) {}
