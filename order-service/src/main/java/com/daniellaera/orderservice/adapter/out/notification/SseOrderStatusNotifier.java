package com.daniellaera.orderservice.adapter.out.notification;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.out.OrderStatusNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseOrderStatusNotifier implements OrderStatusNotifier {

    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    public void statusChanged(Order order) {
        try {
            OrderStatusUpdate update = new OrderStatusUpdate(order.id(), order.status().name(), order.userEmail());
            if (order.userEmail() != null) {
                sseEmitterRegistry.pushToUser(order.userEmail(), update);
            } else {
                sseEmitterRegistry.pushToAll(update);
            }
            log.info("=== SSE: pushed status {} for orderId {}", order.status(), order.id());
        } catch (Exception e) {
            log.warn("=== SSE: push failed for orderId {}: {}", order.id(), e.getMessage());
        }
    }
}
