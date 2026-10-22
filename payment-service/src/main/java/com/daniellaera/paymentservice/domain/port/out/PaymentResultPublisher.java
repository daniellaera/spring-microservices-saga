package com.daniellaera.paymentservice.domain.port.out;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.port.in.InventoryResult;

public interface PaymentResultPublisher {
    /** Propagates failures: a result that cannot be published must not be lost silently. */
    void publish(InventoryResult result, PaymentStatus status);
}
