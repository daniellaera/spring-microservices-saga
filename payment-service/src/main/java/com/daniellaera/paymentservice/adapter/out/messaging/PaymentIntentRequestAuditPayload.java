package com.daniellaera.paymentservice.adapter.out.messaging;

import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;

/** PAYMENT_INITIATED audit payload: the create-intent request as it used to be serialized. */
record PaymentIntentRequestAuditPayload(Long amount, String currency, String productName) {

    static PaymentIntentRequestAuditPayload of(CreatePaymentIntentCommand command) {
        return new PaymentIntentRequestAuditPayload(command.amount(), command.currency(), command.productName());
    }
}
