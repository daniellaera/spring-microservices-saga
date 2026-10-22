package com.daniellaera.inventoryservice.domain.port.in;

/** The part of a payment result that compensation needs. */
public record PaymentOutcome(Long orderId, String status, String userEmail) {

    public boolean failed() {
        return "FAILED".equals(status);
    }
}
