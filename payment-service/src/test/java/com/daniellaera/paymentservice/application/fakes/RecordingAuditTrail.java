package com.daniellaera.paymentservice.application.fakes;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;
import com.daniellaera.paymentservice.domain.port.out.AuditTrail;

import java.util.ArrayList;
import java.util.List;

public class RecordingAuditTrail implements AuditTrail {

    public record Initiated(String paymentIntentId, CreatePaymentIntentCommand command) {}

    public record Processed(PaymentTransaction transaction, String userEmail) {}

    public record DeadLettered(Long orderId, String userEmail, String error) {}

    private final List<Initiated> initiated = new ArrayList<>();
    private final List<Processed> processed = new ArrayList<>();
    private final List<DeadLettered> deadLettered = new ArrayList<>();
    private final EffectLog effectLog;

    public RecordingAuditTrail(EffectLog effectLog) {
        this.effectLog = effectLog;
    }

    @Override
    public void paymentInitiated(String paymentIntentId, CreatePaymentIntentCommand command) {
        initiated.add(new Initiated(paymentIntentId, command));
        effectLog.add("audit PAYMENT_INITIATED");
    }

    @Override
    public void paymentProcessed(PaymentTransaction transaction, String userEmail) {
        processed.add(new Processed(transaction, userEmail));
        effectLog.add("audit " + transaction.status());
    }

    @Override
    public void paymentDeadLettered(Long orderId, String userEmail, String error) {
        deadLettered.add(new DeadLettered(orderId, userEmail, error));
        effectLog.add("audit DEAD_LETTERED");
    }

    public List<DeadLettered> deadLettered() {
        return List.copyOf(deadLettered);
    }

    public List<Initiated> initiated() {
        return List.copyOf(initiated);
    }

    public List<Processed> processed() {
        return List.copyOf(processed);
    }
}
