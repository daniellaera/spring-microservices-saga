package com.daniellaera.paymentservice.domain.port.out;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;

/** Never throws. */
public interface AuditTrail {
    void paymentInitiated(String paymentIntentId, CreatePaymentIntentCommand command);

    void paymentProcessed(PaymentTransaction transaction, String userEmail);
}
