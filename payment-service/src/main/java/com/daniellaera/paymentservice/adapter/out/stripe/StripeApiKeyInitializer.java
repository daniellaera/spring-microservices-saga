package com.daniellaera.paymentservice.adapter.out.stripe;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sets the Stripe SDK's global API key once at startup (static SDK API; StripeClient would remove this). */
@Component
public class StripeApiKeyInitializer {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }
}
