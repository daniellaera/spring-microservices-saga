package com.daniellaera.orderservice.domain.port.out;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;

public interface AuditTrail {
    void orderPlaced(Order order);

    void paymentResultApplied(Order order, PaymentResult result);

    /** An order's event could not be delivered to the saga after the maximum number of attempts. */
    void outboxEventFailed(long orderId, String userEmail, String eventType, int attempts, String error);

    /** A payment result was dead-lettered (orderId null when it cannot be read): the order stays PENDING. */
    void paymentResultDeadLettered(Long orderId, String userEmail, String error);
}
