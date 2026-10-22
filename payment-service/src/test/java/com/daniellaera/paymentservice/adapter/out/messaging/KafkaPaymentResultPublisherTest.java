package com.daniellaera.paymentservice.adapter.out.messaging;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaPaymentResultPublisherTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
    }

    @Test
    void toEvent_passesItemsThroughAndUsesTheStatusName() {
        PaymentEvent event = KafkaPaymentResultPublisher.toEvent(result(List.of(
                new InventoryResult.Item("MacBook Pro", 1, BigDecimal.TEN, BigDecimal.TEN))), PaymentStatus.SUCCESS);

        assertThat(event).isEqualTo(new PaymentEvent(5L, "MacBook Pro", 1, "SUCCESS", BigDecimal.TEN, BigDecimal.TEN,
                "user@test.com", List.of(new OrderItemEvent("MacBook Pro", 1, BigDecimal.TEN, BigDecimal.TEN))));
    }

    @Test
    void toEvent_keepsMissingItemsAsNull() {
        assertThat(KafkaPaymentResultPublisher.toEvent(result(null), PaymentStatus.FAILED).items()).isNull();
    }

    @Test
    void publish_sendsToPaymentTopicWithoutKey() {
        new KafkaPaymentResultPublisher(kafkaTemplate, objectMapper).publish(result(null), PaymentStatus.FAILED);

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    void publish_serializationFailure_propagates() {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new IllegalStateException("cannot serialize"));

        assertThatThrownBy(() -> new KafkaPaymentResultPublisher(kafkaTemplate, failing)
                .publish(result(null), PaymentStatus.SUCCESS))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publish_sendFailure_propagates() {
        when(kafkaTemplate.send(eq("payment-topic"), anyString())).thenThrow(new IllegalStateException("broker down"));

        assertThatThrownBy(() -> new KafkaPaymentResultPublisher(kafkaTemplate, objectMapper)
                .publish(result(null), PaymentStatus.SUCCESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker down");
    }

    private static InventoryResult result(List<InventoryResult.Item> items) {
        return new InventoryResult(5L, "APPROVED", "MacBook Pro", 1, BigDecimal.TEN, BigDecimal.TEN, "user@test.com",
                "pi_1", items);
    }
}
