package com.daniellaera.paymentservice.adapter.out.stripe;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.stripe.Stripe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

/**
 * The warm-up call at the HTTP level, the real Stripe SDK talking to WireMock. Stripe's API base and key are global:
 * both are restored after each test.
 */
@ExtendWith(OutputCaptureExtension.class)
class StripeWarmUpTest {

    @RegisterExtension
    static WireMockExtension stripe = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private String previousApiKey;
    private final StripeWarmUp warmUp = new StripeWarmUp();

    @BeforeEach
    void setUp() {
        previousApiKey = Stripe.apiKey;
        Stripe.apiKey = "sk_test_wiremock";
        Stripe.overrideApiBase(stripe.baseUrl());
        ReflectionTestUtils.setField(warmUp, "secretKey", "sk_test_wiremock");
    }

    @AfterEach
    void tearDown() {
        Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
        Stripe.apiKey = previousApiKey;
    }

    @Test
    void warmUp_listsOnePaymentIntent_authenticated(CapturedOutput output) {
        stripe.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/payment_intents"))
                .willReturn(WireMock.okJson("""
                        {"object":"list","data":[],"has_more":false,"url":"/v1/payment_intents"}""")));

        warmUp.warmUp();

        stripe.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/payment_intents"))
                .withQueryParam("limit", WireMock.equalTo("1"))
                .withHeader("Authorization", WireMock.equalTo("Bearer sk_test_wiremock")));
        assertThat(output.getAll()).contains("=== Stripe: warmed up in");
    }

    @Test
    void aRefusal_isOnlyLogged(CapturedOutput output) {
        stripe.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/payment_intents"))
                .willReturn(WireMock.aResponse().withStatus(401).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"type":"invalid_request_error","message":"Invalid API Key provided"}}""")));

        assertThatCode(warmUp::warmUp).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("=== Stripe: warm-up call failed after")
                .contains("Invalid API Key provided");
    }

    @Test
    void noAnswerAtAll_isOnlyLogged(CapturedOutput output) {
        stripe.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/payment_intents"))
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatCode(warmUp::warmUp).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("=== Stripe: warm-up call failed after");
    }

    @Test
    void onReady_warmsUpOnItsOwnThread_withoutWaitingForStripe() {
        // Stripe answers after 2 s: the ready event must not wait for it
        stripe.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/payment_intents"))
                .willReturn(WireMock.okJson("""
                        {"object":"list","data":[],"has_more":false,"url":"/v1/payment_intents"}""")
                        .withFixedDelay(2000)));

        long start = System.nanoTime();
        warmUp.onReady();
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                stripe.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/payment_intents"))));
    }

    @Test
    void onReady_withoutASecretKey_callsNothing(CapturedOutput output) {
        ReflectionTestUtils.setField(warmUp, "secretKey", "");

        warmUp.onReady();

        assertThat(output.getAll()).contains("=== Stripe: no secret key, warm-up skipped");
        stripe.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }
}
