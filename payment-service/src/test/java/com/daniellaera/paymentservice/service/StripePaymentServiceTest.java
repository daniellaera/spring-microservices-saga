package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.config.StripeConfig;
import com.daniellaera.paymentservice.dto.PaymentIntentRequest;
import com.daniellaera.paymentservice.dto.PaymentIntentResponse;
import com.stripe.exception.ApiException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripePaymentServiceTest {

    @Mock
    private StripeConfig stripeConfig;

    @Mock
    private PaymentIntent paymentIntent;

    @InjectMocks
    private StripePaymentService stripePaymentService;

    private MockedStatic<PaymentIntent> paymentIntentStatic;

    @BeforeEach
    void setUp() {
        paymentIntentStatic = mockStatic(PaymentIntent.class);
    }

    @AfterEach
    void tearDown() {
        paymentIntentStatic.close();
    }

    // ─── createPaymentIntent ───────────────────────────────────────────────

    @Test
    void createPaymentIntent_validRequest_returnsResponse() throws StripeException {
        PaymentIntentRequest request = new PaymentIntentRequest(1999L, "usd", "MacBook Pro");

        when(paymentIntent.getClientSecret()).thenReturn("secret_123");
        when(paymentIntent.getId()).thenReturn("pi_123");
        when(paymentIntent.getStatus()).thenReturn("requires_payment_method");
        when(stripeConfig.getPublishableKey()).thenReturn("pk_test_123");
        paymentIntentStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                .thenReturn(paymentIntent);

        PaymentIntentResponse response = stripePaymentService.createPaymentIntent(request);

        assertThat(response.clientSecret()).isEqualTo("secret_123");
        assertThat(response.paymentIntentId()).isEqualTo("pi_123");
        assertThat(response.status()).isEqualTo("requires_payment_method");
        assertThat(response.publishableKey()).isEqualTo("pk_test_123");
    }

    @Test
    void createPaymentIntent_stripeException_throwsRuntimeException() {
        PaymentIntentRequest request = new PaymentIntentRequest(1999L, "usd", "MacBook Pro");
        StripeException stripeException = new ApiException("card declined", "req_1", "card_error", 402, null);
        paymentIntentStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                .thenThrow(stripeException);

        assertThatThrownBy(() -> stripePaymentService.createPaymentIntent(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("card declined");
    }

    // ─── confirmPayment ─────────────────────────────────────────────────────

    @Test
    void confirmPayment_succeededStatus_returnsTrue() throws StripeException {
        when(paymentIntent.getStatus()).thenReturn("succeeded");
        paymentIntentStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(paymentIntent);

        boolean result = stripePaymentService.confirmPayment("pi_123");

        assertThat(result).isTrue();
    }

    @Test
    void confirmPayment_requiresPaymentMethodStatus_returnsFalse() throws StripeException {
        when(paymentIntent.getStatus()).thenReturn("requires_payment_method");
        paymentIntentStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(paymentIntent);

        boolean result = stripePaymentService.confirmPayment("pi_123");

        assertThat(result).isFalse();
    }

    @Test
    void confirmPayment_canceledStatus_returnsFalse() throws StripeException {
        when(paymentIntent.getStatus()).thenReturn("canceled");
        paymentIntentStatic.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(paymentIntent);

        boolean result = stripePaymentService.confirmPayment("pi_123");

        assertThat(result).isFalse();
    }

    @Test
    void confirmPayment_stripeException_returnsFalseNotRethrown() {
        StripeException stripeException = new ApiException("not found", "req_2", "resource_missing", 404, null);
        paymentIntentStatic.when(() -> PaymentIntent.retrieve("pi_missing")).thenThrow(stripeException);

        boolean result = stripePaymentService.confirmPayment("pi_missing");

        assertThat(result).isFalse();
    }
}
