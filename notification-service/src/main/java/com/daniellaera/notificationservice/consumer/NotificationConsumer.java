package com.daniellaera.notificationservice.consumer;

import com.daniellaera.notificationservice.dto.PaymentEvent;
import com.daniellaera.notificationservice.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final ObjectMapper objectMapper;
    private final EmailNotificationService emailNotificationService;

    @KafkaListener(topics = "payment-topic", groupId = "notification-group")
    public void handlePaymentEvent(String message) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            log.info("=== Notification: orderId={} status={} email={}",
                    event.orderId(), event.status(), event.userEmail());

            if ("SUCCESS".equals(event.status())) {
                emailNotificationService.sendOrderConfirmed(event);
            } else if ("FAILED".equals(event.status())) {
                emailNotificationService.sendOrderCancelled(event);
            } else {
                log.warn("=== Unknown status: {}", event.status());
            }
        } catch (Exception e) {
            log.error("=== Failed to process payment event: {}", e.getMessage());
        }
    }
}
