package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.port.in.PaymentOutcome;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentEventListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<PaymentOutcome> outcomes = new ArrayList<>();
    private final PaymentEventListener listener = new PaymentEventListener(outcomes::add, objectMapper);

    @Test
    void paymentEvent_isMappedToAnOutcome() throws Exception {
        listener.handlePaymentResult("{\"orderId\":6,\"productName\":\"MacBook Pro\",\"quantity\":2,\"status\":\"FAILED\","
                + "\"price\":1299.99,\"totalAmount\":3349.95,\"userEmail\":\"user@test.com\",\"items\":[]}");

        assertThat(outcomes).containsExactly(new PaymentOutcome(6L, "FAILED", "user@test.com"));
    }

    @Test
    void malformedJson_propagatesToTheContainer() {
        assertThatThrownBy(() -> listener.handlePaymentResult("not json")).isInstanceOf(JacksonException.class);
        assertThat(outcomes).isEmpty();
    }

    @Test
    void useCaseFailure_propagatesToTheContainer() {
        PaymentEventListener failing = new PaymentEventListener(outcome -> {
            throw new IllegalStateException("db down");
        }, objectMapper);

        assertThatThrownBy(() -> failing.handlePaymentResult("{\"orderId\":6,\"status\":\"FAILED\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }
}
