package com.daniellaera.paymentservice.domain.port.in;

public record CreatedPaymentIntent(String clientSecret, String id, String status) {
}
