package com.daniellaera.paymentservice.application.fakes;

import com.daniellaera.paymentservice.domain.model.IntentVerification;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayException;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayUnavailableException;
import com.daniellaera.paymentservice.domain.model.PaymentRefund;
import com.daniellaera.paymentservice.domain.model.PaymentRefundRefusedException;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Intents are known by id; an unknown id behaves like a provider error (empty), as the Stripe adapter does. The
 * HTTP-level behaviour of the real adapter is pinned by StripeGatewayITTest.
 */
public class FakePaymentGateway implements PaymentGateway {

    public record IntentRequest(long amount, String currency, String description) {}

    public record RefundRequest(String intentId, String idempotencyKey) {}

    private final Map<String, IntentVerification> intents = new HashMap<>();
    private final List<IntentRequest> intentRequests = new ArrayList<>();
    private final List<String> verifiedIntents = new ArrayList<>();
    private int unavailableAnswers;
    private final List<RefundRequest> refundRequests = new ArrayList<>();
    private final Map<String, PaymentRefund> refundsByKey = new HashMap<>();
    private final Map<String, String> refusedRefunds = new HashMap<>();
    private final Set<String> alreadyRefunded = new HashSet<>();
    private int unavailableRefunds;
    private final EffectLog effectLog;

    public FakePaymentGateway() {
        this(new EffectLog());
    }

    /** Records each refund in the effect log, to check when it happens. */
    public FakePaymentGateway(EffectLog effectLog) {
        this.effectLog = effectLog;
    }
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

    /** Stripe refuses to refund this intent for good, with this error code. */
    public void refundRefused(String intentId, String errorCode) {
        refusedRefunds.put(intentId, errorCode);
    }

    /** The intent's payment was refunded outside the shop (e.g. by hand in the dashboard). */
    public void refundedOutsideTheShop(String intentId) {
        alreadyRefunded.add(intentId);
    }

    /** The next n refunds throw PaymentGatewayUnavailableException. */
    public void refundsUnavailableFor(int refunds) {
        unavailableRefunds = refunds;
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

    // Like Stripe: the same idempotency key gets the same refund back
    @Override
    public PaymentRefund refund(String intentId, String idempotencyKey) {
        refundRequests.add(new RefundRequest(intentId, idempotencyKey));
        if (unavailableRefunds > 0) {
            unavailableRefunds--;
            throw new PaymentGatewayUnavailableException("Stripe unavailable: 503", null);
        }
        if (refusedRefunds.containsKey(intentId)) {
            throw new PaymentRefundRefusedException(intentId, refusedRefunds.get(intentId), "refund refused");
        }
        if (alreadyRefunded.contains(intentId)) {
            return PaymentRefund.alreadyRefunded();
        }
        effectLog.add("refund " + intentId);
        return refundsByKey.computeIfAbsent(idempotencyKey,
                key -> new PaymentRefund("re_" + (refundsByKey.size() + 1) + "_" + intentId, "succeeded"));
    }

    public List<RefundRequest> refundRequests() {
        return List.copyOf(refundRequests);
    }

    /** The distinct refunds made, one per idempotency key. */
    public List<PaymentRefund> refunds() {
        return List.copyOf(refundsByKey.values());
    }

    public List<IntentRequest> intentRequests() {
        return List.copyOf(intentRequests);
    }

    public List<String> verifiedIntents() {
        return List.copyOf(verifiedIntents);
    }
}
