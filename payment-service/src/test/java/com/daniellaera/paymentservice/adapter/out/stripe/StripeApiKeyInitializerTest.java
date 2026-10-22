package com.daniellaera.paymentservice.adapter.out.stripe;

import com.stripe.Stripe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class StripeApiKeyInitializerTest {

    private String previousApiKey;
    private String previousApiBase;

    @BeforeEach
    void saveStripeGlobals() {
        previousApiKey = Stripe.apiKey;
        previousApiBase = Stripe.getApiBase();
    }

    @AfterEach
    void restoreStripeGlobals() {
        Stripe.apiKey = previousApiKey;
        Stripe.overrideApiBase(previousApiBase);
    }

    @Test
    void init_setsTheGlobalStripeApiKey() {
        StripeApiKeyInitializer initializer = new StripeApiKeyInitializer();
        ReflectionTestUtils.setField(initializer, "secretKey", "sk_test_from_config");

        initializer.init();

        assertThat(Stripe.apiKey).isEqualTo("sk_test_from_config");
    }

    @Test
    void init_withoutApiBase_keepsTheLiveApiBase() {
        Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
        StripeApiKeyInitializer initializer = new StripeApiKeyInitializer();
        ReflectionTestUtils.setField(initializer, "secretKey", "sk_test_from_config");
        ReflectionTestUtils.setField(initializer, "apiBase", "");

        initializer.init();

        assertThat(Stripe.getApiBase()).isEqualTo(Stripe.LIVE_API_BASE);
    }

    @Test
    void init_withApiBase_pointsTheSdkAtIt() {
        StripeApiKeyInitializer initializer = new StripeApiKeyInitializer();
        ReflectionTestUtils.setField(initializer, "secretKey", "sk_test_from_config");
        ReflectionTestUtils.setField(initializer, "apiBase", "http://wiremock:8080");

        initializer.init();

        assertThat(Stripe.getApiBase()).isEqualTo("http://wiremock:8080");
    }
}
