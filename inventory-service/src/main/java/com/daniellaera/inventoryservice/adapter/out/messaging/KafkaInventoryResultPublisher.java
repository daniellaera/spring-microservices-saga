package com.daniellaera.inventoryservice.adapter.out.messaging;

import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.out.InventoryResultPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaInventoryResultPublisher implements InventoryResultPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(ReserveStockCommand command, ReservationDecision decision) {
        InventoryResultEvent result = toEvent(command, decision);
        try {
            kafkaTemplate.send("inventory-topic", objectMapper.writeValueAsString(result));
            log.info("=== Inventory: published {} to inventory-topic for orderId {}",
                    result.status(), result.orderId());
        } catch (Exception e) {
            log.error("=== Inventory: failed to publish inventory result for orderId {}: {}",
                    result.orderId(), e.getMessage());
        }
    }

    // quantity is the legacy single-item field: the requested quantity when approved, 0 when rejected
    static InventoryResultEvent toEvent(ReserveStockCommand command, ReservationDecision decision) {
        return new InventoryResultEvent(
                command.orderId(),
                decision.name(),
                command.productName(),
                decision == ReservationDecision.APPROVED ? command.quantity() : Integer.valueOf(0),
                command.price(),
                command.totalAmount(),
                command.userEmail(),
                command.paymentIntentId(),
                command.lines().stream().map(OrderItemEvents::of).toList()
        );
    }
}
