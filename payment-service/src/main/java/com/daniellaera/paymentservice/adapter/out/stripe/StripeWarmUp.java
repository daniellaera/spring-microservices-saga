package com.daniellaera.paymentservice.adapter.out.stripe;

import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentListParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * One cheap authenticated Stripe call once payment-service is ready, so that the first buyer after a restart does not
 * wait for the SDK's first call (class loading, DNS, TLS handshake): it took over 1 s on 2026-10-23. It lists one
 * PaymentIntent, the API that create-intent and the payment check use. It runs on its own thread with short timeouts,
 * so it never delays startup, and a failure is only logged: the buyer's own call simply pays the cost instead.
 */
@Component
@Slf4j
public class StripeWarmUp {

    private static final RequestOptions OPTIONS = RequestOptions.builder()
            .setConnectTimeout(5_000)
            .setReadTimeout(10_000)
            .build();

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (secretKey == null || secretKey.isBlank()) {
            log.info("=== Stripe: no secret key, warm-up skipped");
            return;
        }
        Thread.ofVirtual().name("stripe-warm-up").start(this::warmUp);
    }

    // the API key is the SDK's global one, set by StripeApiKeyInitializer before the application is ready
    void warmUp() {
        long start = System.nanoTime();
        try {
            PaymentIntent.list(PaymentIntentListParams.builder().setLimit(1L).build(), OPTIONS);
            log.info("=== Stripe: warmed up in {} ms", elapsedMillis(start));
        } catch (Exception e) {
            log.warn("=== Stripe: warm-up call failed after {} ms (the first payment will be slower): {}",
                    elapsedMillis(start), e.getMessage());
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
