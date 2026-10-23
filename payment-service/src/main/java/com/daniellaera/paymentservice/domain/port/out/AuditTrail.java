package com.daniellaera.paymentservice.domain.port.out;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;

/** Never throws. */
public interface AuditTrail {
    void paymentInitiated(String paymentIntentId, CreatePaymentIntentCommand command);

    void paymentProcessed(PaymentTransaction transaction, String userEmail);

    /** orderId and userEmail may be null (an unreadable dead-lettered message). */
    void paymentDeadLettered(Long orderId, String userEmail, String error);
}
