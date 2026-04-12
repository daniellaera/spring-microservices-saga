package com.daniellaera.orderservice.consumer;

import com.daniellaera.orderservice.dto.PaymentEvent;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderConsumer {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-topic", groupId = "order-group")
    public void handlePaymentEvent(String message) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);

            orderRepository.findById(event.orderId()).ifPresent(order -> {
                if ("SUCCESS".equals(event.status())) {
                    order.setStatus(OrderStatus.CONFIRMED);
                } else {
                    order.setStatus(OrderStatus.CANCELLED);
                }
                orderRepository.save(order);
            });
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", e.getMessage());
        }
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
        // log to DB or alert system in real world
    }
}
