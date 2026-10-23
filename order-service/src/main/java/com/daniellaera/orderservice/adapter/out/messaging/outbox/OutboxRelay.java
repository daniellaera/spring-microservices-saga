package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.adapter.out.messaging.KafkaOrderEventSender;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEvent;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEventNotSentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

/**
 * Delivers outbox rows to orders-topic in creation order, at least once. A row is marked published only once
 * Kafka acknowledged it. When a send fails or times out, the batch stops so that no later event overtakes it,
 * and the row is retried on the next tick. A crash between the acknowledgement and the commit sends the event
 * again: inventory-service skips an order it already reserved (existsByOrderId), so a duplicate is harmless.
 * A payload that cannot be read can never be sent: it is logged and left unpublished, and later rows still go
 * out (it must not block every later order).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaOrderEventSender kafkaOrderEventSender;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEventJpaEntity> unpublished = outboxEventJpaRepository.findByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEventJpaEntity event : unpublished) {
            OrderEvent orderEvent;
            try {
                orderEvent = objectMapper.readValue(event.getPayload(), OrderEvent.class);
            } catch (Exception e) {
                log.error("=== Outbox: unreadable payload in event {} for orderId {}, left unpublished: {}",
                        event.getId(), event.getAggregateId(), e.getMessage());
                continue;
            }
            try {
                kafkaOrderEventSender.send(orderEvent);
            } catch (OrderEventNotSentException e) {
                log.error("=== Outbox: {}; stopping this batch, retrying on the next tick", e.getMessage());
                return;
            }
            event.setPublished(true);
            event.setPublishedAt(Instant.now());
            outboxEventJpaRepository.save(event);
            log.info("=== Outbox: published event for orderId: {}", event.getAggregateId());
        }
    }
}
