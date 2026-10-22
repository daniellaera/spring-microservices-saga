package com.daniellaera.sagae2e.support;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/** WireMock plays Stripe (payment intent lookups) and the config server (an empty environment). */
public final class WireMockStubs {

    public static final String ACCEPTED_INTENT = "pi_e2e_ok";
    public static final String DECLINED_INTENT = "pi_e2e_declined";

    private WireMockStubs() {
    }

    static void install(String host, int port) {
        WireMock wireMock = new WireMock(host, port);

        // Same response shape as payment-service's StripeGatewayITTest
        stubPaymentIntent(wireMock, ACCEPTED_INTENT, "succeeded");
        stubPaymentIntent(wireMock, DECLINED_INTENT, "requires_payment_method");

        // GET /{application}/{profile}: no property sources, every setting comes from the container environment
        wireMock.register(get(urlPathMatching("/[a-z-]+-service/.*")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"name":"e2e","profiles":["default"],"label":null,"version":null,"state":null,
                         "propertySources":[]}""")));
    }

    private static void stubPaymentIntent(WireMock wireMock, String id, String status) {
        wireMock.register(get(urlEqualTo("/v1/payment_intents/" + id)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"status\":\"" + status + "\"}")));
    }
}
