package com.daniellaera.paymentservice.application.fakes;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;
import com.daniellaera.paymentservice.domain.port.out.AuditTrail;

import java.util.ArrayList;
import java.util.List;

public class RecordingAuditTrail implements AuditTrail {

    public record Initiated(String paymentIntentId, CreatePaymentIntentCommand command) {}

    public record Processed(PaymentTransaction transaction, String userEmail) {}

    private final List<Initiated> initiated = new ArrayList<>();
    private final List<Processed> processed = new ArrayList<>();
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

    public List<Initiated> initiated() {
        return List.copyOf(initiated);
    }

    public List<Processed> processed() {
        return List.copyOf(processed);
    }
}
