package com.daniellaera.orderservice.consumer;

import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "payment-topic", groupId = "order-group")
    public void handlePaymentEvent(String message) {
        String[] parts = message.split(":");
        Long orderId = Long.parseLong(parts[0]);
        String paymentStatus = parts[1];

        orderRepository.findById(orderId).ifPresent(order -> {
            if ("SUCCESS".equals(paymentStatus)) {
                order.setStatus(OrderStatus.CONFIRMED);
            } else {
                order.setStatus(OrderStatus.CANCELLED);
            }
            orderRepository.save(order);
        });
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
        // log to DB or alert system in real world
    }
}
