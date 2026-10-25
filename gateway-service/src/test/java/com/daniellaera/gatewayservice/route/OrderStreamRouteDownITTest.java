package com.daniellaera.gatewayservice.route;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.ServerSocket;

/** GET /orders/stream while order-service is down (a restart): a 503 the browser reconnects after, not a 500. */
class OrderStreamRouteDownITTest extends RouteITSupport {

    @BeforeAll
    static void pointTheOrderRoutesAtAClosedPort() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        System.setProperty("ORDER_SERVICE_URI", "http://localhost:" + closedPort);
    }

    @AfterAll
    static void clearOrderServiceUri() {
        System.clearProperty("ORDER_SERVICE_URI");
    }

    @Test
    void streamWhileOrderServiceIsDown_isAnswered503ByTheOrderStreamFallback() {
        client().get().uri("/orders/stream")
                .header("Authorization", "Bearer " + token())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.message").isEqualTo("Order updates are currently unavailable. Reconnecting shortly.");
    }
}
