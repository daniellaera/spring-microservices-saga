package com.daniellaera.paymentservice.domain.port.in;

/** amount is in the currency's smallest unit, as the payment provider expects it. */
public record CreatePaymentIntentCommand(Long amount, String currency, String productName, String userEmail) {
}
