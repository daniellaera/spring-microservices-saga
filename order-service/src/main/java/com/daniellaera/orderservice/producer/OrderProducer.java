package com.daniellaera.orderservice.producer;

import com.daniellaera.orderservice.dto.OrderEvent;
import com.daniellaera.orderservice.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendOrder(Order order) {
        OrderEvent event = new OrderEvent(
                order.getId(),
                order.getProductName(),
                order.getQuantity()
        );
        String message = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("orders-topic", message);
    }
}