package com.daniellaera.orderservice.adapter.in.messaging;

import com.daniellaera.orderservice.domain.port.in.ApplyPaymentResultUseCase;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final ApplyPaymentResultUseCase applyPaymentResultUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-topic", groupId = "order-group")
    public void handlePaymentEvent(String message) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            applyPaymentResultUseCase.applyPaymentResult(new PaymentResult(event.orderId(), event.productName(),
                    event.quantity(), event.status(), event.price(), event.totalAmount(), event.userEmail()));
        } catch (Exception e) {
            log.error("=== Kafka: failed to process payment event: {}", e.getMessage());
        }
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
        // log to DB or alert system in real world
    }
}
