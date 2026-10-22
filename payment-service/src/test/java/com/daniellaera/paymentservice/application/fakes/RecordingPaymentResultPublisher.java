package com.daniellaera.paymentservice.application.fakes;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import com.daniellaera.paymentservice.domain.port.out.PaymentResultPublisher;

import java.util.ArrayList;
import java.util.List;

public class RecordingPaymentResultPublisher implements PaymentResultPublisher {

    public record Published(InventoryResult result, PaymentStatus status) {}

    private final List<Published> published = new ArrayList<>();
    private final EffectLog effectLog;
    private RuntimeException failure;

    public RecordingPaymentResultPublisher(EffectLog effectLog) {
        this.effectLog = effectLog;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public void publish(InventoryResult result, PaymentStatus status) {
        if (failure != null) {
            throw failure;
        }
        published.add(new Published(result, status));
        effectLog.add("publish " + status);
    }

    public List<Published> published() {
        return List.copyOf(published);
    }
}
