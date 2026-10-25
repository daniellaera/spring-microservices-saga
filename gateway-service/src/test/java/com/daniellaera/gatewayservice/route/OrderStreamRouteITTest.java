package com.daniellaera.gatewayservice.route;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/** GET /orders/stream through the order-stream-cb breaker, with the stub playing order-service. */
class OrderStreamRouteITTest extends RouteITSupport {

    private static final String EVENTS = """
            data:{"orderId":1,"status":"CONFIRMED"}

            data:{"orderId":2,"status":"CANCELLED"}

            """;

    @BeforeAll
    static void pointTheOrderRoutesAtTheStub() {
        System.setProperty("ORDER_SERVICE_URI", backend.baseUrl());
    }

    @AfterAll
    static void clearOrderServiceUri() {
        System.clearProperty("ORDER_SERVICE_URI");
    }

    @Test
    void aStreamOutlivingTheOneSecondLimitOfTheOtherRoutes_isDeliveredWhole() {
        // 1.5 s before the first byte, then the events dribbled over 2 s: well past resilience4j's 1 s default
        backend.stubFor(WireMock.get(WireMock.urlEqualTo("/orders/stream"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .withBody(EVENTS)
                        .withFixedDelay(1500)
                        .withChunkedDribbleDelay(4, 2000)));

        long start = System.nanoTime();
        String body = client().get().uri("/orders/stream")
                .header("Authorization", "Bearer " + token())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isEqualTo(EVENTS);
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(3500);
    }
}
