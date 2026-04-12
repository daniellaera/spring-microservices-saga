package com.daniellaera.paymentservice.dto;

public record PaymentEvent(Long orderId, String productName, Integer quantity, String status) {}
