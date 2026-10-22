package com.daniellaera.inventoryservice.adapter.out.messaging;

import com.daniellaera.inventoryservice.domain.model.OrderLine;

final class OrderItemEvents {

    private OrderItemEvents() {
    }

    static OrderItemEvent of(OrderLine line) {
        return new OrderItemEvent(line.productName(), line.quantity(), line.price(), line.totalAmount());
    }
}
