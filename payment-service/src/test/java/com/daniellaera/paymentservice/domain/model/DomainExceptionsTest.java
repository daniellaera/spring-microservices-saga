package com.daniellaera.paymentservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The messages are part of the HTTP contract (404 body) and of the logs. */
class DomainExceptionsTest {

    @Test
    void transactionNotFound_namesTheId() {
        assertThat(new TransactionNotFoundException(99L)).hasMessage("Transaction not found with id: 99");
    }

    @Test
    void paymentGatewayException_keepsMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("card declined");

        assertThat(new PaymentGatewayException("Stripe payment failed: card declined", cause))
                .hasMessage("Stripe payment failed: card declined")
                .hasCause(cause);
    }
}
