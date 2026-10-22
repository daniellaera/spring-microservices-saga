package com.daniellaera.orderservice.adapter.out.messaging;

import com.daniellaera.orderservice.domain.model.Order;

import java.util.List;

public final class OrderEventMapper {

    private OrderEventMapper() {
    }

    public static OrderEvent toEvent(Order order) {
        List<OrderItemEvent> items = order.items().stream()
                .map(i -> new OrderItemEvent(i.productName(), i.quantity(), i.price(), i.totalAmount()))
                .toList();

        return new OrderEvent(order.id(), order.productName(), order.quantity(), order.price(), order.totalAmount(),
                order.userEmail(), order.paymentIntentId(), items);
    }
}
