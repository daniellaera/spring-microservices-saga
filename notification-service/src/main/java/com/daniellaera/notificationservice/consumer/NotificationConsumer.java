package com.daniellaera.notificationservice.consumer;

import com.daniellaera.notificationservice.dto.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-topic", groupId = "notification-group")
    public void consume(String message) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            log.info("Notification — Order #{} for '{}' x{} is {}",
                    event.orderId(), event.productName(), event.quantity(), event.status());
        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage());
        }
    }
}
