package com.daniellaera.notificationservice.adapter.in.messaging;

import com.daniellaera.notificationservice.domain.model.PaymentOutcome;
import com.daniellaera.notificationservice.domain.port.in.NotifyPaymentOutcomeUseCase;
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
    private final NotifyPaymentOutcomeUseCase notifyPaymentOutcomeUseCase;

    @KafkaListener(topics = "payment-topic", groupId = "notification-group")
    public void handlePaymentEvent(String message) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            log.info("=== Notification: orderId={} status={} email={}",
                    event.orderId(), event.status(), event.userEmail());

            notifyPaymentOutcomeUseCase.notifyOutcome(toOutcome(event));
        } catch (Exception e) {
            log.error("=== Failed to process payment event: {}", e.getMessage());
        }
    }

    static PaymentOutcome toOutcome(PaymentEvent event) {
        return new PaymentOutcome(event.orderId(), event.productName(), event.quantity(), event.price(),
                event.totalAmount(), event.status(), event.userEmail(),
                event.items() == null ? null : event.items().stream()
                        .map(item -> new PaymentOutcome.Item(item.productName(), item.quantity(), item.price(),
                                item.totalAmount()))
                        .toList());
    }
}
