package com.daniellaera.inventoryservice.consumer;

import com.daniellaera.inventoryservice.dto.OrderEvent;
import com.daniellaera.inventoryservice.model.Product;
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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "orders-topic", groupId = "inventory-group")
    public void consumeOrder(String message) {
        OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

        Optional<Product> productOpt = productRepository.findByName(event.productName());

        if (productOpt.isPresent() && productOpt.get().getQuantity() >= event.quantity()) {
            Product product = productOpt.get();
            product.setQuantity(product.getQuantity() - event.quantity());
            productRepository.save(product);
            kafkaTemplate.send("inventory-topic", event.orderId() + ":APPROVED");
        } else {
            kafkaTemplate.send("inventory-topic", event.orderId() + ":REJECTED");
        }
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }
}