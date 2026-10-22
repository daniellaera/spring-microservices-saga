package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.port.in.CompensateStockUseCase;
import com.daniellaera.inventoryservice.domain.port.in.PaymentOutcome;
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

    private final CompensateStockUseCase compensateStockUseCase;
    private final ObjectMapper objectMapper;

    // Unlike the order listener, nothing is caught here: failures go to the container's error handler
    @KafkaListener(topics = "payment-topic", groupId = "inventory-compensation-group")
    public void handlePaymentResult(String message) throws Exception {
        PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
        compensateStockUseCase.compensate(new PaymentOutcome(event.orderId(), event.status(), event.userEmail()));
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }
}
