package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final ReserveStockUseCase reserveStockUseCase;
    private final ObjectMapper objectMapper;

    // Every failure is logged and swallowed, including an unknown product (the order then stays PENDING)
    @KafkaListener(topics = "orders-topic", groupId = "inventory-group")
    public void consumeOrder(String message) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            reserveStockUseCase.reserve(toCommand(event));
        } catch (Exception e) {
            log.error("=== Inventory: failed to process order event: {}", e.getMessage());
        }
    }

    static ReserveStockCommand toCommand(OrderEvent event) {
        List<OrderLine> lines = resolveItems(event.items(), event.productName(), event.quantity(),
                event.price(), event.totalAmount()).stream()
                .map(item -> new OrderLine(item.productName(), item.quantity(), item.price(), item.totalAmount()))
                .toList();
        return new ReserveStockCommand(event.orderId(), event.productName(), event.quantity(), event.price(),
                event.totalAmount(), event.userEmail(), event.paymentIntentId(), lines);
    }

    // Orders from before multi-item support carry only the legacy single-item fields
    private static List<OrderItemEvent> resolveItems(List<OrderItemEvent> items, String productName, Integer quantity,
                                                     BigDecimal price, BigDecimal totalAmount) {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        return List.of(new OrderItemEvent(productName, quantity, price, totalAmount));
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }
}
