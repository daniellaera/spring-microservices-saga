package com.daniellaera.gatewayservice.route;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The gateway's payments routes, with the stub playing payment-service. */
abstract class PaymentsRouteITSupport extends RouteITSupport {

    @BeforeAll
    static void pointThePaymentRoutesAtTheStub() {
        System.setProperty("PAYMENT_SERVICE_URI", backend.baseUrl());
    }

    @AfterAll
    static void clearPaymentServiceUri() {
        System.clearProperty("PAYMENT_SERVICE_URI");
    }

    void paymentServiceAnswersCreateIntentAfter(int delayMillis) {
        backend.stubFor(WireMock.post(WireMock.urlEqualTo("/payments/create-intent"))
                .willReturn(WireMock.okJson("""
                        {"clientSecret":"pi_1_secret_1","paymentIntentId":"pi_1"}""")
                        .withFixedDelay(delayMillis)));
    }

    WebTestClient.ResponseSpec createIntent() {
        return client()
                .post().uri("/payments/create-intent")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount":129999,"currency":"eur","productName":"MacBook Pro"}""")
                .exchange();
    }
}
