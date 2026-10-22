package com.daniellaera.paymentservice.adapter.in.messaging;

import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import com.daniellaera.paymentservice.domain.port.in.ProcessInventoryResultUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryResultEventListener {

    private final ProcessInventoryResultUseCase processInventoryResultUseCase;
    private final ObjectMapper objectMapper;

    // Nothing is caught (current behaviour): parse, save and publish failures go to the container's error handler
    @KafkaListener(topics = "inventory-topic", groupId = "payment-group")
    public void handleInventoryResult(String message) throws Exception {
        InventoryResultEvent event = objectMapper.readValue(message, InventoryResultEvent.class);
        processInventoryResultUseCase.process(toResult(event));
    }

    // items are passed through untouched, null included
    static InventoryResult toResult(InventoryResultEvent event) {
        return new InventoryResult(event.orderId(), event.status(), event.productName(), event.quantity(),
                event.price(), event.totalAmount(), event.userEmail(), event.paymentIntentId(),
                event.items() == null ? null : event.items().stream()
                        .map(item -> new InventoryResult.Item(item.productName(), item.quantity(), item.price(),
                                item.totalAmount()))
                        .toList());
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }
}
