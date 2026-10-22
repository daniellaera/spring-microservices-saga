package com.daniellaera.sagae2e.support;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/** WireMock plays Stripe (payment intent lookups) and the config server (an empty environment). */
public final class WireMockStubs {

    private final WireMock wireMock;

    private WireMockStubs(WireMock wireMock) {
        this.wireMock = wireMock;
    }

    static WireMockStubs install(String host, int port) {
        WireMock wireMock = new WireMock(host, port);

        // GET /{application}/{profile}: no property sources, every setting comes from the container environment
        wireMock.register(get(urlPathMatching("/[a-z-]+-service/.*")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"name":"e2e","profiles":["default"],"label":null,"version":null,"state":null,
                         "propertySources":[]}""")));
        return new WireMockStubs(wireMock);
    }

    /**
     * What Stripe answers for GET /v1/payment_intents/{id}. Same response shape as payment-service's
     * StripeGatewayITTest. payment-service checks status, amount (minor units) and currency against the order.
     */
    public void stubPaymentIntent(String id, String status, long amountMinor, String currency) {
        wireMock.register(get(urlEqualTo("/v1/payment_intents/" + id)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"status\":\"" + status
                        + "\",\"amount\":" + amountMinor + ",\"currency\":\"" + currency + "\"}")));
    }
}
