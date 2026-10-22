package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxOrderEventPublisherTest {

    private final OutboxEventJpaRepository outboxRepository = mock(OutboxEventJpaRepository.class);
    private final Order order = Order.place("user@test.com", null, List.of(OrderItem.of("Item", 1, new BigDecimal("10.00"))));

    @Test
    void serializationFailure_isRethrownAsFailedToSaveOutboxEvent() {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> new OutboxOrderEventPublisher(outboxRepository, failingMapper).orderPlaced(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to save outbox event")
                .hasCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void saveFailure_isRethrownAsFailedToSaveOutboxEvent() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        when(outboxRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> new OutboxOrderEventPublisher(outboxRepository, objectMapper).orderPlaced(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to save outbox event");
    }
}
