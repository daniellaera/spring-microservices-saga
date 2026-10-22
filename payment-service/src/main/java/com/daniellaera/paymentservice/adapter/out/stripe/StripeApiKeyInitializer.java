package com.daniellaera.paymentservice.adapter.out.stripe;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sets the Stripe SDK's global API key once at startup (static SDK API; StripeClient would remove this). The
 * optional stripe.api-base points the SDK at a fake Stripe in test environments; it must never be set in prod.
 */
@Component
public class StripeApiKeyInitializer {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.api-base:}")
    private String apiBase;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        if (apiBase != null && !apiBase.isBlank()) {
            Stripe.overrideApiBase(apiBase);
        }
    }
}
