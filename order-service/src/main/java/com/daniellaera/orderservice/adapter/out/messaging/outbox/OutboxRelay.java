package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.adapter.out.messaging.KafkaOrderEventSender;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

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
            try {
                OrderEvent orderEvent = objectMapper.readValue(event.getPayload(), OrderEvent.class);
                kafkaOrderEventSender.send(orderEvent);
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxEventJpaRepository.save(event);
                log.info("=== Outbox: published event for orderId: {}", event.getAggregateId());
            } catch (Exception e) {
                log.error("=== Outbox: failed to publish event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
