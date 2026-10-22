package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.adapter.out.messaging.OrderEventMapper;
import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.out.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxOrderEventPublisher implements OrderEventPublisher {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    // The outbox row must commit or roll back with the order itself
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void orderPlaced(Order order) {
        try {
            OutboxEventJpaEntity outboxEvent = new OutboxEventJpaEntity();
            outboxEvent.setAggregateId(order.id());
            outboxEvent.setEventType("ORDER_CREATED");
            outboxEvent.setPayload(objectMapper.writeValueAsString(OrderEventMapper.toEvent(order)));
            outboxEventJpaRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("=== Outbox: failed to persist outbox event for orderId {}: {}", order.id(), e.getMessage());
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }
}
