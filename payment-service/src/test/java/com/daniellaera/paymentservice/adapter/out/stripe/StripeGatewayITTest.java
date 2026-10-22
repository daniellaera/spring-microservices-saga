package com.daniellaera.paymentservice.adapter.out.stripe;

import com.daniellaera.paymentservice.audit.AuditPublisher;
import com.daniellaera.paymentservice.config.StripeConfig;
import com.daniellaera.paymentservice.controller.StripePaymentController;
import com.daniellaera.paymentservice.exception.GlobalExceptionHandler;
import com.daniellaera.paymentservice.service.StripePaymentService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.stripe.Stripe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the Stripe calls at the HTTP level: the real Stripe SDK talks to WireMock (Stripe.overrideApiBase), so the
 * exact requests sent to Stripe and the handling of its answers are pinned independently of how the code is
 * organised. The endpoints are driven through MockMvc so the assertions survive the move behind a gateway port.
 * Stripe's API base and key are global: both are restored after each test.
 */
class StripeGatewayITTest {

    @RegisterExtension
    static WireMockExtension stripe = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private String previousApiKey;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        previousApiKey = Stripe.apiKey;
        Stripe.apiKey = "sk_test_wiremock";
        Stripe.overrideApiBase(stripe.baseUrl());

        StripeConfig stripeConfig = new StripeConfig();
        ReflectionTestUtils.setField(stripeConfig, "publishableKey", "pk_test_wiremock");
        StripePaymentService stripePaymentService = new StripePaymentService(stripeConfig, mock(AuditPublisher.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new StripePaymentController(stripePaymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
        Stripe.apiKey = previousApiKey;
    }

    @Test
    void createIntent_sendsExactlyTheseParametersAndReturnsTheIntent() throws Exception {
        stripe.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/payment_intents")).willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id":"pi_wm_1","object":"payment_intent","client_secret":"pi_wm_1_secret_abc",
                         "status":"requires_payment_method","amount":1999,"currency":"usd"}""")));

        mockMvc.perform(post("/payments/create-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Email", "user@test.com")
                        .content("{\"amount\":1999,\"currency\":\"usd\",\"productName\":\"MacBook Pro\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"clientSecret":"pi_wm_1_secret_abc","paymentIntentId":"pi_wm_1",
                         "status":"requires_payment_method","publishableKey":"pk_test_wiremock"}""", true));

        LoggedRequest request = singleRequest();
        assertThat(request.getMethod().getName()).isEqualTo("POST");
        assertThat(request.getUrl()).isEqualTo("/v1/payment_intents");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk_test_wiremock");
        assertThat(formParameters(request)).isEqualTo(Map.of(
                "amount", "1999",
                "currency", "usd",
                "description", "Purchase: MacBook Pro",
                "automatic_payment_methods[enabled]", "true"));
    }

    @Test
    void createIntent_stripeError_returnsTheGeneric500() throws Exception {
        stripe.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/payment_intents")).willReturn(WireMock.aResponse()
                .withStatus(402)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error":{"type":"card_error","code":"card_declined","message":"Your card was declined."}}""")));

        mockMvc.perform(post("/payments/create-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1999,\"currency\":\"usd\",\"productName\":\"MacBook Pro\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("{\"status\":500,\"message\":\"Internal server error\"}"));
    }

    @Test
    void confirm_succeededIntent_isASuccess() throws Exception {
        stubRetrieve("pi_wm_1", "succeeded");

        mockMvc.perform(get("/payments/confirm/pi_wm_1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"success\":true}", true));

        LoggedRequest request = singleRequest();
        assertThat(request.getMethod().getName()).isEqualTo("GET");
        assertThat(request.getUrl()).isEqualTo("/v1/payment_intents/pi_wm_1");
    }

    @Test
    void confirm_anyOtherStatus_isNotASuccess() throws Exception {
        stubRetrieve("pi_wm_2", "requires_payment_method");

        mockMvc.perform(get("/payments/confirm/pi_wm_2"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"success\":false}", true));
    }

    @Test
    void confirm_stripeError_isNotASuccess() throws Exception {
        stripe.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/payment_intents/pi_missing")).willReturn(WireMock.aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"error":{"type":"invalid_request_error","code":"resource_missing",
                         "message":"No such payment_intent: 'pi_missing'"}}""")));

        mockMvc.perform(get("/payments/confirm/pi_missing"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"success\":false}", true));
    }

    private static void stubRetrieve(String id, String intentStatus) {
        stripe.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/payment_intents/" + id)).willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"status\":\"" + intentStatus + "\"}")));
    }

    private static LoggedRequest singleRequest() {
        List<LoggedRequest> requests = stripe.findAll(WireMock.anyRequestedFor(WireMock.anyUrl()));
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    private static Map<String, String> formParameters(LoggedRequest request) {
        return Arrays.stream(request.getBodyAsString().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
