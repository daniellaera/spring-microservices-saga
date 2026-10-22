package com.daniellaera.paymentservice.adapter.in.messaging;

import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryResultEventListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<InventoryResult> results = new ArrayList<>();
    private final InventoryResultEventListener listener = new InventoryResultEventListener(results::add, objectMapper);

    @Test
    void inventoryResult_mapsEveryFieldAndItem() throws Exception {
        listener.handleInventoryResult("{\"orderId\":5,\"status\":\"APPROVED\",\"productName\":\"MacBook Pro\","
                + "\"quantity\":1,\"price\":1299.99,\"totalAmount\":1299.99,\"userEmail\":\"user@test.com\","
                + "\"paymentIntentId\":\"pi_999\",\"items\":[{\"productName\":\"MacBook Pro\",\"quantity\":1,"
                + "\"price\":1299.99,\"totalAmount\":1299.99}]}");

        assertThat(results).containsExactly(new InventoryResult(5L, "APPROVED", "MacBook Pro", 1,
                new BigDecimal("1299.99"), new BigDecimal("1299.99"), "user@test.com", "pi_999", List.of(
                        new InventoryResult.Item("MacBook Pro", 1, new BigDecimal("1299.99"),
                                new BigDecimal("1299.99")))));
    }

    @Test
    void withoutItems_keepsThemNull() throws Exception {
        listener.handleInventoryResult("{\"orderId\":4,\"status\":\"REJECTED\"}");

        assertThat(results).singleElement().satisfies(result -> assertThat(result.items()).isNull());
    }

    @Test
    void malformedJson_propagatesToTheContainer() {
        assertThatThrownBy(() -> listener.handleInventoryResult("not json")).isInstanceOf(JacksonException.class);
        assertThat(results).isEmpty();
    }

    @Test
    void useCaseFailure_propagatesToTheContainer() {
        InventoryResultEventListener failing = new InventoryResultEventListener(result -> {
            throw new IllegalStateException("db down");
        }, objectMapper);

        assertThatThrownBy(() -> failing.handleInventoryResult("{\"orderId\":4,\"status\":\"REJECTED\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }
}
