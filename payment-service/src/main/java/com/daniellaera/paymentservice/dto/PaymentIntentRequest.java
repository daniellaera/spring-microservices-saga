package com.daniellaera.paymentservice.dto;

public record PaymentIntentRequest(
        Long amount,
        String currency,
        String productName
) {}
