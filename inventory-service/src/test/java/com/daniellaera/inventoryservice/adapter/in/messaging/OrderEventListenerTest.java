package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.UnknownProductException;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OrderEventListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<ReserveStockCommand> commands = new ArrayList<>();
    private final OrderEventListener listener = new OrderEventListener(command -> {
        commands.add(command);
        return Optional.empty();
    }, objectMapper);

    @Test
    void multiItemOrder_mapsEveryFieldAndItem() {
        listener.consumeOrder("{\"orderId\":4,\"productName\":\"MacBook Pro\",\"quantity\":2,\"price\":1299.99,"
                + "\"totalAmount\":3349.95,\"userEmail\":\"user@test.com\",\"paymentIntentId\":\"pi_multi\",\"items\":["
                + "{\"productName\":\"MacBook Pro\",\"quantity\":2,\"price\":1299.99,\"totalAmount\":2599.98},"
                + "{\"productName\":\"AirPods\",\"quantity\":3,\"price\":249.99,\"totalAmount\":749.97}]}");

        assertThat(commands).containsExactly(new ReserveStockCommand(4L, "MacBook Pro", 2, new BigDecimal("1299.99"),
                new BigDecimal("3349.95"), "user@test.com", "pi_multi", List.of(
                        new OrderLine("MacBook Pro", 2, new BigDecimal("1299.99"), new BigDecimal("2599.98")),
                        new OrderLine("AirPods", 3, new BigDecimal("249.99"), new BigDecimal("749.97")))));
    }

    @Test
    void legacyOrderWithoutItems_resolvesOneLineFromTheLegacyFields() {
        listener.consumeOrder("{\"orderId\":1,\"productName\":\"MacBook Pro\",\"quantity\":2,\"price\":1299.99,"
                + "\"totalAmount\":2599.98,\"userEmail\":\"user@test.com\"}");

        assertThat(commands).singleElement().satisfies(command -> assertThat(command.lines()).containsExactly(
                new OrderLine("MacBook Pro", 2, new BigDecimal("1299.99"), new BigDecimal("2599.98"))));
    }

    @Test
    void emptyItems_alsoFallBackToTheLegacyFields() {
        listener.consumeOrder("{\"orderId\":1,\"productName\":\"MacBook Pro\",\"quantity\":2,\"price\":1299.99,"
                + "\"totalAmount\":2599.98,\"items\":[]}");

        assertThat(commands).singleElement()
                .satisfies(command -> assertThat(command.lines()).extracting(OrderLine::productName)
                        .containsExactly("MacBook Pro"));
    }

    @Test
    void malformedJson_isSwallowed() {
        assertThatCode(() -> listener.consumeOrder("not json")).doesNotThrowAnyException();

        assertThat(commands).isEmpty();
    }

    @Test
    void useCaseFailure_isSwallowed() {
        OrderEventListener failing = new OrderEventListener(command -> {
            throw new UnknownProductException("Unknown Product");
        }, objectMapper);

        assertThatCode(() -> failing.consumeOrder("{\"orderId\":3,\"productName\":\"Unknown Product\",\"quantity\":1}"))
                .doesNotThrowAnyException();
    }
}
