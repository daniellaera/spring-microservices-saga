package com.daniellaera.inventoryservice.adapter.out.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaAuditPublisherTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaAuditPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new KafkaAuditPublisher(kafkaTemplate, objectMapper);
    }

    @Test
    void publish_isAsync() throws Exception {
        assertThat(KafkaAuditPublisher.class
                .getMethod("publish", String.class, String.class, String.class, String.class, Long.class, Object.class)
                .isAnnotationPresent(Async.class)).isTrue();
    }

    @Test
    void publish_withoutUserOrEntityId_usesSystemAndEmptyId() {
        publisher.publish("PRODUCT_CREATED", null, "PRODUCT", null, null, Map.of());

        Map<?, ?> message = sentMessage();
        assertThat(message.get("userEmail")).isEqualTo("system");
        assertThat(message.get("entityId")).isEqualTo("");
        assertThat(message.get("serviceName")).isEqualTo("inventory-service");
        // outside a saga: no correlation id at all, not a null one
        assertThat(message.containsKey("correlationId")).isFalse();
    }

    @Test
    void publish_ofASagaEvent_carriesTheOrderIdAsCorrelationId() {
        publisher.publish("STOCK_DEDUCTED", "user@test.com", "PRODUCT", "11", 42L, Map.of());

        assertThat(sentMessage().get("correlationId")).isEqualTo("42");
    }

    @Test
    void kafkaFailure_isSwallowed() {
        when(kafkaTemplate.send(eq("audit-topic"), anyString())).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> publisher.publish("PRODUCT_CREATED", null, "PRODUCT", "1", null, Map.of()))
                .doesNotThrowAnyException();
    }

    private Map<?, ?> sentMessage() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("audit-topic"), message.capture());
        return objectMapper.readValue(message.getValue(), Map.class);
    }
}
