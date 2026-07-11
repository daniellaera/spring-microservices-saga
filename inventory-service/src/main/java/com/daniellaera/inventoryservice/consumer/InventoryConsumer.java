package com.daniellaera.inventoryservice.consumer;

import com.daniellaera.inventoryservice.dto.InventoryResultEvent;
import com.daniellaera.inventoryservice.dto.OrderEvent;
import com.daniellaera.inventoryservice.dto.OrderItemEvent;
import com.daniellaera.inventoryservice.dto.PaymentEvent;
import com.daniellaera.inventoryservice.model.CompensationLog;
import com.daniellaera.inventoryservice.model.Product;
import com.daniellaera.inventoryservice.repository.CompensationLogRepository;
import com.daniellaera.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryConsumer {

    private final ProductRepository productRepository;
    private final CompensationLogRepository compensationLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "orders-topic", groupId = "inventory-group")
    public void consumeOrder(String message) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            List<OrderItemEvent> items = resolveItems(event.items(), event.productName(), event.quantity(),
                    event.price(), event.totalAmount());
            log.info("=== Inventory: received order {} with {} item(s)", event.orderId(), items.size());

            // resolve products up front — product-not-found aborts processing entirely (no publish)
            List<Product> products = items.stream()
                    .map(item -> productRepository.findByName(item.productName())
                            .orElseThrow(() -> new RuntimeException("Product not found: " + item.productName())))
                    .toList();

            boolean sufficientStock = true;
            for (int i = 0; i < items.size(); i++) {
                if (products.get(i).getQuantity() < items.get(i).quantity()) {
                    sufficientStock = false;
                    break;
                }
            }

            String status;
            int reservedQuantity;
            if (sufficientStock) {
                for (int i = 0; i < items.size(); i++) {
                    Product product = products.get(i);
                    OrderItemEvent item = items.get(i);
                    product.setQuantity(product.getQuantity() - item.quantity());
                    productRepository.save(product);
                    log.info("=== Inventory: deducted {} x{} — {} remaining",
                            item.productName(), item.quantity(), product.getQuantity());
                }
                status = "APPROVED";
                reservedQuantity = event.quantity();
                log.info("=== Inventory: stock reserved for orderId {}", event.orderId());
            } else {
                status = "REJECTED";
                reservedQuantity = 0;
                log.warn("=== Inventory: insufficient stock for orderId {} — REJECTING order", event.orderId());
            }

            InventoryResultEvent result = new InventoryResultEvent(
                    event.orderId(),
                    status,
                    event.productName(),
                    reservedQuantity,
                    event.price(),
                    event.totalAmount(),
                    event.userEmail(),
                    event.paymentIntentId(),
                    items
            );

            kafkaTemplate.send("inventory-topic", objectMapper.writeValueAsString(result));
            log.info("=== Inventory: published {} to inventory-topic for orderId {}",
                    status, event.orderId());

        } catch (Exception e) {
            log.error("=== Inventory: failed to process order event: {}", e.getMessage());
        }
    }

    private List<OrderItemEvent> resolveItems(List<OrderItemEvent> items, String productName, Integer quantity,
                                               BigDecimal price, BigDecimal totalAmount) {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        return List.of(new OrderItemEvent(productName, quantity, price, totalAmount));
    }

    @KafkaListener(topics = "payment-topic", groupId = "inventory-compensation-group")
    public void handlePaymentResult(String message) throws Exception {
        PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);

        log.info("=== Received payment result for orderId: {} status: {}", event.orderId(), event.status());

        if (!"FAILED".equals(event.status())) {
            log.info("=== Payment SUCCESS — no compensation needed for orderId: {}", event.orderId());
            return;
        }

        if (compensationLogRepository.existsByOrderId(event.orderId())) {
            log.warn("=== Compensation already applied for orderId: {} — skipping", event.orderId());
            return;
        }

        List<OrderItemEvent> items = resolveItems(event.items(), event.productName(), event.quantity(),
                event.price(), event.totalAmount());
        log.info("=== Payment FAILED — compensating {} item(s) for orderId {}", items.size(), event.orderId());

        for (OrderItemEvent item : items) {
            productRepository.findByName(item.productName()).ifPresentOrElse(
                    product -> {
                        product.setQuantity(product.getQuantity() + item.quantity());
                        productRepository.save(product);
                        log.info("=== Compensation SUCCESS — {} stock restored to {}", item.productName(), product.getQuantity());
                    },
                    () -> log.error("=== Compensation FAILED — product {} not found", item.productName())
            );
        }

        CompensationLog entry = new CompensationLog();
        entry.setOrderId(event.orderId());
        entry.setProductName(event.productName());
        entry.setQuantity(event.quantity());
        compensationLogRepository.save(entry);
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }
}
