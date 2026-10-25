package com.daniellaera.orderservice.adapter.out.messaging;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaAuditTrailTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaAuditTrail auditTrail;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        auditTrail = new KafkaAuditTrail(kafkaTemplate, objectMapper);
    }

    @Test
    void paymentResultApplied_cancelledOrder_publishesOrderCancelled() {
        auditTrail.paymentResultApplied(order(OrderStatus.CANCELLED, "user@test.com"), failedPayment());

        Map<?, ?> message = sentMessage();
        assertThat(message.get("eventType")).isEqualTo("ORDER_CANCELLED");
        assertThat(message.get("entityType")).isEqualTo("ORDER");
        assertThat(message.get("entityId")).isEqualTo("5");
        assertThat(message.get("serviceName")).isEqualTo("order-service");
    }

    @Test
    void orderWithoutEmail_isAuditedAsSystem() {
        auditTrail.orderPlaced(order(OrderStatus.PENDING, null));

        assertThat(sentMessage().get("userEmail")).isEqualTo("system");
    }

    @Test
    void kafkaFailure_isSwallowed() {
        when(kafkaTemplate.send(eq("audit-topic"), anyString())).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> auditTrail.orderPlaced(order(OrderStatus.PENDING, "user@test.com")))
                .doesNotThrowAnyException();
        assertThatCode(() -> auditTrail.paymentResultApplied(order(OrderStatus.CONFIRMED, "user@test.com"), failedPayment()))
                .doesNotThrowAnyException();
    }

    @Test
    void outboxEventFailed_publishesTheOrderTheEventAttemptsAndError() {
        auditTrail.outboxEventFailed(77L, null, "ORDER_CREATED", 5, "unreadable payload: Unexpected character");

        Map<?, ?> message = sentMessage();
        assertThat(message.get("eventType")).isEqualTo("OUTBOX_EVENT_FAILED");
        assertThat(message.get("entityType")).isEqualTo("ORDER");
        assertThat(message.get("entityId")).isEqualTo("77");
        assertThat(message.get("userEmail")).isEqualTo("system");
        assertThat(message.get("serviceName")).isEqualTo("order-service");
        assertThat(objectMapper.readValue((String) message.get("payload"), Map.class)).isEqualTo(Map.of(
                "orderId", 77, "eventType", "ORDER_CREATED", "attempts", 5,
                "error", "unreadable payload: Unexpected character"));
    }

    @Test
    void paymentResultDeadLettered_publishesTheOrderAndError_withTheOrderAsCorrelationId() {
        auditTrail.paymentResultDeadLettered(7L, "user@test.com", "db down");

        Map<?, ?> message = sentMessage();
        assertThat(message.get("eventType")).isEqualTo("ORDER_DEAD_LETTERED");
        assertThat(message.get("entityType")).isEqualTo("ORDER");
        assertThat(message.get("entityId")).isEqualTo("7");
        assertThat(message.get("correlationId")).isEqualTo("7");
        assertThat(message.get("userEmail")).isEqualTo("user@test.com");
        assertThat(objectMapper.readValue((String) message.get("payload"), Map.class))
                .isEqualTo(Map.of("orderId", 7, "error", "db down"));
    }

    @Test
    void anUnreadableDeadLetteredResult_hasNoOrder() {
        auditTrail.paymentResultDeadLettered(null, null, "Unexpected character");

        Map<?, ?> message = sentMessage();
        assertThat(message.get("entityId")).isEqualTo("");
        assertThat(message.containsKey("correlationId")).isFalse();
        assertThat(message.get("userEmail")).isEqualTo("system");
    }

    private Map<?, ?> sentMessage() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("audit-topic"), message.capture());
        return objectMapper.readValue(message.getValue(), Map.class);
    }

    private static Order order(OrderStatus status, String userEmail) {
        return Order.restore(5L, userEmail, null, status, new BigDecimal("10.00"), null,
                "Item", 1, new BigDecimal("10.00"), List.of(new OrderItem(1L, "Item", 1, new BigDecimal("10.00"), new BigDecimal("10.00"))));
    }

    private static PaymentResult failedPayment() {
        return new PaymentResult(5L, "Item", 1, "FAILED", new BigDecimal("10.00"), new BigDecimal("10.00"), "user@test.com");
    }
}
