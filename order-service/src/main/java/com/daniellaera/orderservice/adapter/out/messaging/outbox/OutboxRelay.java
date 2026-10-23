package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.adapter.out.messaging.KafkaOrderEventSender;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEvent;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEventNotSentException;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

/**
 * Delivers outbox rows to orders-topic in creation order, at least once. A row is marked published only once
 * Kafka acknowledged it. When a send fails or times out, the batch stops so that no later event overtakes it,
 * and the row is retried on the next tick. A crash between the acknowledgement and the commit sends the event
 * again: inventory-service skips an order it already reserved (existsByOrderId), so a duplicate is harmless.
 * <p>
 * A row that can never be sent counts its attempts: a payload that cannot be read (later rows still go out, it
 * must not block every later order) or a record Kafka refuses as such (see OrderEventNotSentException#permanent).
 * After maxAttempts it is marked FAILED, no longer tried, and audited as OUTBOX_EVENT_FAILED. A transient failure
 * (timeout, broker unreachable) never counts: an outage longer than maxAttempts ticks must not fail good rows.
 */
@Component
@Slf4j
public class OutboxRelay {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaOrderEventSender kafkaOrderEventSender;
    private final ObjectMapper objectMapper;
    private final AuditTrail auditTrail;
    private final int maxAttempts;

    public OutboxRelay(OutboxEventJpaRepository outboxEventJpaRepository, KafkaOrderEventSender kafkaOrderEventSender,
                       ObjectMapper objectMapper, AuditTrail auditTrail,
                       @Value("${outbox.relay.max-attempts:5}") int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("outbox.relay.max-attempts must be at least 1, was " + maxAttempts);
        }
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.kafkaOrderEventSender = kafkaOrderEventSender;
        this.objectMapper = objectMapper;
        this.auditTrail = auditTrail;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEventJpaEntity> pending =
                outboxEventJpaRepository.findByPublishedFalseAndStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEventJpaEntity event : pending) {
            OrderEvent orderEvent;
            try {
                orderEvent = objectMapper.readValue(event.getPayload(), OrderEvent.class);
            } catch (Exception e) {
                failedAttempt(event, null, "unreadable payload: " + e.getMessage());
                continue;
            }
            try {
                kafkaOrderEventSender.send(orderEvent);
            } catch (OrderEventNotSentException e) {
                if (e.permanent()) {
                    failedAttempt(event, orderEvent.userEmail(), e.getMessage());
                }
                log.error("=== Outbox: {}; stopping this batch, retrying on the next tick", e.getMessage());
                return;
            }
            event.markPublished(Instant.now());
            outboxEventJpaRepository.save(event);
            log.info("=== Outbox: published event for orderId: {}", event.getAggregateId());
        }
    }

    private void failedAttempt(OutboxEventJpaEntity event, String userEmail, String error) {
        boolean failed = event.recordFailedAttempt(error, maxAttempts);
        outboxEventJpaRepository.save(event);
        if (!failed) {
            log.error("=== Outbox: event {} for orderId {} not sent, attempt {} of {}: {}", event.getId(),
                    event.getAggregateId(), event.getAttempts(), maxAttempts, error);
            return;
        }
        log.error("=== Outbox: event {} for orderId {} FAILED after {} attempts, no longer retried: {}",
                event.getId(), event.getAggregateId(), event.getAttempts(), error);
        Runnable audit = () -> auditTrail.outboxEventFailed(event.getAggregateId(), userEmail, event.getEventType(),
                event.getAttempts(), error);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // only once the FAILED status is committed: a rolled-back tick tries the row again
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    audit.run();
                }
            });
        } else {
            audit.run();
        }
    }
}
