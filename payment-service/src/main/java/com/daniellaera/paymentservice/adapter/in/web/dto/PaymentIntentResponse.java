package com.daniellaera.paymentservice.adapter.in.web.dto;

public record PaymentIntentResponse(
        String clientSecret,
        String paymentIntentId,
        String status,
        String publishableKey
) {}
