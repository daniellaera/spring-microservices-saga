package com.daniellaera.orderservice.application.fakes;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;

import java.util.ArrayList;
import java.util.List;

public class RecordingAuditTrail implements AuditTrail {

    public record PaymentAudit(Order order, PaymentResult result) {}

    private final List<Order> placed = new ArrayList<>();
    private final List<PaymentAudit> paymentResults = new ArrayList<>();

    @Override
    public void orderPlaced(Order order) {
        placed.add(order);
    }

    @Override
    public void paymentResultApplied(Order order, PaymentResult result) {
        paymentResults.add(new PaymentAudit(order, result));
    }

    public List<Order> placed() {
        return List.copyOf(placed);
    }

    public List<PaymentAudit> paymentResults() {
        return List.copyOf(paymentResults);
    }
}
