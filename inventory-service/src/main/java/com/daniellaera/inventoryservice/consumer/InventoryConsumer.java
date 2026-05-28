package com.daniellaera.inventoryservice.consumer;

import com.daniellaera.inventoryservice.dto.InventoryEvent;
import com.daniellaera.inventoryservice.dto.OrderEvent;
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

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryConsumer {

    private final ProductRepository productRepository;
    private final CompensationLogRepository compensationLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "orders-topic", groupId = "inventory-group")
    public void consumeOrder(String message) throws Exception {
        OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

        Optional<Product> productOpt = productRepository.findByName(event.productName());

        if (productOpt.isPresent() && productOpt.get().getQuantity() >= event.quantity()) {
            Product product = productOpt.get();
            product.setQuantity(product.getQuantity() - event.quantity());
            productRepository.save(product);
            kafkaTemplate.send("inventory-topic", objectMapper.writeValueAsString(
                    new InventoryEvent(event.orderId(), event.productName(), event.quantity(), "APPROVED",
                            event.price(), event.totalAmount(), event.userEmail())
            ));
        } else {
            kafkaTemplate.send("inventory-topic", objectMapper.writeValueAsString(
                    new InventoryEvent(event.orderId(), event.productName(), 0, "REJECTED",
                            event.price(), event.totalAmount(), event.userEmail())
            ));
        }
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

        log.info("=== Payment FAILED — compensating: restoring {} units of {}", event.quantity(), event.productName());

        productRepository.findByName(event.productName()).ifPresentOrElse(
                product -> {
                    product.setQuantity(product.getQuantity() + event.quantity());
                    productRepository.save(product);

                    CompensationLog entry = new CompensationLog();
                    entry.setOrderId(event.orderId());
                    entry.setProductName(event.productName());
                    entry.setQuantity(event.quantity());
                    compensationLogRepository.save(entry);

                    log.info("=== Compensation SUCCESS — {} stock restored to {}", event.productName(), product.getQuantity());
                },
                () -> log.error("=== Compensation FAILED — product {} not found", event.productName())
        );
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }
}
