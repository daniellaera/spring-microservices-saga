package com.daniellaera.paymentservice.dto;

public record PaymentIntentResponse(
        String clientSecret,
        String paymentIntentId,
        String status,
        String publishableKey
) {}
