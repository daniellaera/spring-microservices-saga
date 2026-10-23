package com.daniellaera.paymentservice.domain.port.out;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;

/** Never throws. */
public interface AuditTrail {
    void paymentInitiated(String paymentIntentId, CreatePaymentIntentCommand command);

    void paymentProcessed(PaymentTransaction transaction, String userEmail);

    /** The captured payment of a failed order was refunded (or had already been, refundId ALREADY_REFUNDED). */
    void paymentRefunded(PaymentTransaction transaction, String userEmail);

    /** The provider refused to refund the captured payment of a failed order: it needs a manual refund. */
    void paymentRefundFailed(PaymentTransaction transaction, String errorCode, String userEmail);

    /** orderId and userEmail may be null (an unreadable dead-lettered message). */
    void paymentDeadLettered(Long orderId, String userEmail, String error);
}
