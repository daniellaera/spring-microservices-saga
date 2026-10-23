package com.daniellaera.orderservice.adapter.out.messaging;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaAuditTrail implements AuditTrail {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async
    @Override
    public void orderPlaced(Order order) {
        publish("ORDER_CREATED", order, () -> OrderEventMapper.toEvent(order));
    }

    @Async
    @Override
    public void paymentResultApplied(Order order, PaymentResult result) {
        publish(order.status() == OrderStatus.CONFIRMED ? "ORDER_CONFIRMED" : "ORDER_CANCELLED", order, () -> result);
    }

    @Async
    @Override
    public void outboxEventFailed(long orderId, String userEmail, String eventType, int attempts, String error) {
        publish("OUTBOX_EVENT_FAILED", orderId, userEmail,
                () -> new OutboxFailedAuditPayload(orderId, eventType, attempts, error));
    }

    private void publish(String eventType, Order order, Supplier<Object> payload) {
        publish(eventType, order.id(), order.userEmail(), payload);
    }

    private void publish(String eventType, Long orderId, String userEmail, Supplier<Object> payload) {
        try {
            Map<String, Object> auditMessage = Map.of(
                    "eventType", eventType,
                    "userEmail", userEmail != null ? userEmail : "system",
                    "entityType", "ORDER",
                    "entityId", String.valueOf(orderId),
                    "payload", objectMapper.writeValueAsString(payload.get()),
                    "serviceName", "order-service",
                    "timestamp", LocalDateTime.now().toString()
            );
            kafkaTemplate.send("audit-topic", objectMapper.writeValueAsString(auditMessage));
        } catch (Exception e) {
            log.warn("=== Audit: failed to publish event: {}", e.getMessage());
            // never throw
        }
    }
}
