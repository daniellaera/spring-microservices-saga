package com.daniellaera.inventoryservice.adapter.out.messaging;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaInventoryResultPublisherTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaInventoryResultPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new KafkaInventoryResultPublisher(kafkaTemplate, objectMapper);
    }

    @Test
    void approved_echoesTheLegacyQuantity() {
        InventoryResultEvent event = KafkaInventoryResultPublisher.toEvent(command(3), ReservationDecision.APPROVED);

        assertThat(event.status()).isEqualTo("APPROVED");
        assertThat(event.quantity()).isEqualTo(3);
        assertThat(event.items()).containsExactly(
                new OrderItemEvent("MacBook Pro", 3, new BigDecimal("10.00"), new BigDecimal("30.00")));
    }

    @Test
    void rejected_hasQuantityZero() {
        InventoryResultEvent event = KafkaInventoryResultPublisher.toEvent(command(3), ReservationDecision.REJECTED);

        assertThat(event.status()).isEqualTo("REJECTED");
        assertThat(event.quantity()).isZero();
        assertThat(event.items()).hasSize(1);
    }

    @Test
    void rejected_withoutLegacyQuantity_stillHasQuantityZero() {
        InventoryResultEvent event = KafkaInventoryResultPublisher.toEvent(command(null), ReservationDecision.REJECTED);

        assertThat(event.quantity()).isZero();
    }

    @Test
    void publish_sendsToInventoryTopicWithoutKey() {
        publisher.publish(command(3), ReservationDecision.APPROVED);

        verify(kafkaTemplate).send(eq("inventory-topic"), anyString());
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    void publish_kafkaFailure_isSwallowed() {
        when(kafkaTemplate.send(eq("inventory-topic"), anyString())).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> publisher.publish(command(3), ReservationDecision.APPROVED)).doesNotThrowAnyException();
    }

    private static ReserveStockCommand command(Integer legacyQuantity) {
        return new ReserveStockCommand(1L, "MacBook Pro", legacyQuantity, new BigDecimal("10.00"),
                new BigDecimal("30.00"), "user@test.com", "pi_1",
                List.of(new OrderLine("MacBook Pro", 3, new BigDecimal("10.00"), new BigDecimal("30.00"))));
    }
}
