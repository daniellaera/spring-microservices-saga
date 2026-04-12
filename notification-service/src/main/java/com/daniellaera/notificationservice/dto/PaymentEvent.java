package com.daniellaera.notificationservice.dto;

public record PaymentEvent(
        Long orderId,
        String productName,
        Integer quantity,
        String status
) {}