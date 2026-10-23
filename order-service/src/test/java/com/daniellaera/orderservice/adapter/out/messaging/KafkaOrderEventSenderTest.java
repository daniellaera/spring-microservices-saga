package com.daniellaera.orderservice.adapter.out.messaging;

import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaOrderEventSenderTest {

    private static final OrderEvent EVENT = new OrderEvent(42L, "Item", 1, BigDecimal.TEN, BigDecimal.TEN,
            "user@test.com", "pi_42", List.of());

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaOrderEventSender sender;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        sender = new KafkaOrderEventSender(kafkaTemplate, objectMapper, Duration.ofMillis(200));
    }

    @Test
    void acknowledged_returns() {
        when(kafkaTemplate.send(eq("orders-topic"), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        assertThatCode(() -> sender.send(EVENT)).doesNotThrowAnyException();
    }

    @Test
    void failedSend_throws() {
        when(kafkaTemplate.send(eq("orders-topic"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Expiring 1 record(s)")));

        assertThatThrownBy(() -> sender.send(EVENT))
                .isInstanceOf(OrderEventNotSentException.class)
                .hasMessage("Order event for orderId 42 was not sent: TimeoutException Expiring 1 record(s)");
    }

    @Test
    void noAcknowledgementWithinTheTimeout_throws() {
        when(kafkaTemplate.send(eq("orders-topic"), anyString())).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> sender.send(EVENT))
                .isInstanceOf(OrderEventNotSentException.class)
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    @Test
    void producerRefusesTheRecord_throws() {
        // e.g. max.block.ms exceeded while the broker is unreachable
        when(kafkaTemplate.send(eq("orders-topic"), anyString()))
                .thenThrow(new KafkaException("Send failed"));

        assertThatThrownBy(() -> sender.send(EVENT))
                .isInstanceOf(OrderEventNotSentException.class)
                .hasCauseInstanceOf(KafkaException.class);
    }
}
