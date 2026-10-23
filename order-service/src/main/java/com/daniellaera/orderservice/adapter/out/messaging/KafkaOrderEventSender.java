package com.daniellaera.orderservice.adapter.out.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends synchronously: returns only once Kafka has acknowledged the event, and throws OrderEventNotSentException
 * when the send fails or is not acknowledged within the timeout, so the outbox relay never marks an event
 * published that Kafka did not take.
 */
@Component
public class KafkaOrderEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Duration sendTimeout;

    public KafkaOrderEventSender(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                 @Value("${outbox.relay.send-timeout:5s}") Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sendTimeout = sendTimeout;
    }

    public void send(OrderEvent event) {
        String message = objectMapper.writeValueAsString(event);
        try {
            kafkaTemplate.send("orders-topic", message).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderEventNotSentException(event.orderId(), e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new OrderEventNotSentException(event.orderId(), e);
        }
    }
}
