package com.daniellaera.paymentservice.adapter.out.stripe;

import com.stripe.Stripe;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class StripeApiKeyInitializerTest {

    @Test
    void init_setsTheGlobalStripeApiKey() {
        String previous = Stripe.apiKey;
        try {
            StripeApiKeyInitializer initializer = new StripeApiKeyInitializer();
            ReflectionTestUtils.setField(initializer, "secretKey", "sk_test_from_config");

            initializer.init();

            assertThat(Stripe.apiKey).isEqualTo("sk_test_from_config");
        } finally {
            Stripe.apiKey = previous;
        }
    }
}
