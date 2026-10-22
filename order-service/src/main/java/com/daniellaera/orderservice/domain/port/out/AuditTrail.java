package com.daniellaera.orderservice.domain.port.out;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;

public interface AuditTrail {
    void orderPlaced(Order order);

    void paymentResultApplied(Order order, PaymentResult result);
}
