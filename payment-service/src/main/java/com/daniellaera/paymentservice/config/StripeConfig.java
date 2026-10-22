package com.daniellaera.paymentservice.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** The publishable key handed to the browser. The secret key is set by adapter.out.stripe.StripeApiKeyInitializer. */
@Configuration
public class StripeConfig {

    @Getter
    @Value("${stripe.publishable-key:}")
    private String publishableKey;
}
