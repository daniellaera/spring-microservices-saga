package com.daniellaera.paymentservice.application.fakes;

import com.daniellaera.paymentservice.domain.model.PaymentGatewayException;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Intents are known by id with a status; an unknown id behaves like a provider error (false), as the Stripe
 * adapter does. The HTTP-level behaviour of the real adapter is pinned by StripeGatewayITTest.
 */
public class FakePaymentGateway implements PaymentGateway {

    public record IntentRequest(long amount, String currency, String description) {}

    private final Map<String, String> intentStatuses = new HashMap<>();
    private final List<IntentRequest> intentRequests = new ArrayList<>();
    private final List<String> verifiedIntents = new ArrayList<>();
    private boolean createFails;

    public void givenIntent(String id, String status) {
        intentStatuses.put(id, status);
    }

    public void failIntentCreation() {
        createFails = true;
    }

    @Override
    public CreatedPaymentIntent createIntent(long amount, String currency, String description) {
        intentRequests.add(new IntentRequest(amount, currency, description));
        if (createFails) {
            throw new PaymentGatewayException("Stripe payment failed: card declined", null);
        }
        return new CreatedPaymentIntent("pi_fake_secret", "pi_fake", "requires_payment_method");
    }

    @Override
    public boolean isSucceeded(String intentId) {
        verifiedIntents.add(intentId);
        return "succeeded".equals(intentStatuses.get(intentId));
    }

    public List<IntentRequest> intentRequests() {
        return List.copyOf(intentRequests);
    }

    public List<String> verifiedIntents() {
        return List.copyOf(verifiedIntents);
    }
}
