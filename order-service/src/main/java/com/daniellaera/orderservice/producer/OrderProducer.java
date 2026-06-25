package com.daniellaera.orderservice.producer;

import com.daniellaera.orderservice.dto.OrderEvent;
import com.daniellaera.orderservice.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendOrder(Order order) {
        sendOrderEvent(new OrderEvent(
                order.getId(),
                order.getProductName(),
                order.getQuantity(),
                order.getPrice(),
                order.getTotalAmount(),
                order.getUserEmail(),
                order.getPaymentIntentId()
        ));
    }

    public void sendOrderEvent(OrderEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("orders-topic", message);
        } catch (Exception e) {
            log.error("Failed to send order event: {}", e.getMessage());
        }
    }
}
