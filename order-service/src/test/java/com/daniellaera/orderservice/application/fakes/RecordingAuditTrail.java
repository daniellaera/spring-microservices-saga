package com.daniellaera.orderservice.application.fakes;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;

import java.util.ArrayList;
import java.util.List;

public class RecordingAuditTrail implements AuditTrail {

    public record PaymentAudit(Order order, PaymentResult result) {}

    public record OutboxFailure(long orderId, String userEmail, String eventType, int attempts, String error) {}

    public record DeadLetter(Long orderId, String userEmail, String error) {}

    private final List<Order> placed = new ArrayList<>();
    private final List<PaymentAudit> paymentResults = new ArrayList<>();
    private final List<OutboxFailure> outboxFailures = new ArrayList<>();
    private final List<DeadLetter> deadLetters = new ArrayList<>();

    @Override
    public void orderPlaced(Order order) {
        placed.add(order);
    }

    @Override
    public void paymentResultApplied(Order order, PaymentResult result) {
        paymentResults.add(new PaymentAudit(order, result));
    }

    @Override
    public void outboxEventFailed(long orderId, String userEmail, String eventType, int attempts, String error) {
        outboxFailures.add(new OutboxFailure(orderId, userEmail, eventType, attempts, error));
    }

    @Override
    public void paymentResultDeadLettered(Long orderId, String userEmail, String error) {
        deadLetters.add(new DeadLetter(orderId, userEmail, error));
    }

    public List<DeadLetter> deadLetters() {
        return List.copyOf(deadLetters);
    }

    public List<OutboxFailure> outboxFailures() {
        return List.copyOf(outboxFailures);
    }

    public List<Order> placed() {
        return List.copyOf(placed);
    }

    public List<PaymentAudit> paymentResults() {
        return List.copyOf(paymentResults);
    }
}
