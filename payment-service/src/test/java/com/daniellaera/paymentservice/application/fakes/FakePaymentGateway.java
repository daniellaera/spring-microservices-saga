package com.daniellaera.paymentservice.application.fakes;

import com.daniellaera.paymentservice.domain.model.IntentVerification;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayException;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayUnavailableException;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Intents are known by id; an unknown id behaves like a provider error (empty), as the Stripe adapter does. The
 * HTTP-level behaviour of the real adapter is pinned by StripeGatewayITTest.
 */
public class FakePaymentGateway implements PaymentGateway {

    public record IntentRequest(long amount, String currency, String description) {}

    private final Map<String, IntentVerification> intents = new HashMap<>();
    private final List<IntentRequest> intentRequests = new ArrayList<>();
    private final List<String> verifiedIntents = new ArrayList<>();
    private int unavailableAnswers;
    private boolean createFails;

    /** An intent known by its status only (no amount nor currency): it never pays for an order. */
    public void givenIntent(String id, String status) {
        intents.put(id, new IntentVerification(status, null, null));
    }

    public void givenIntent(String id, String status, long amountMinor, String currency) {
        intents.put(id, new IntentVerification(status, amountMinor, currency));
    }

    /** The next n verifications throw PaymentGatewayUnavailableException, as the Stripe adapter does on a 5xx. */
    public void unavailableFor(int verifications) {
        unavailableAnswers = verifications;
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
    public Optional<IntentVerification> verifyIntent(String intentId) {
        verifiedIntents.add(intentId);
        if (unavailableAnswers > 0) {
            unavailableAnswers--;
            throw new PaymentGatewayUnavailableException("Stripe unavailable: 503", null);
        }
        return Optional.ofNullable(intents.get(intentId));
    }

    public List<IntentRequest> intentRequests() {
        return List.copyOf(intentRequests);
    }

    public List<String> verifiedIntents() {
        return List.copyOf(verifiedIntents);
    }
}
