package com.daniellaera.orderservice.domain.port.out;

import com.daniellaera.orderservice.domain.model.Order;

public interface OrderStatusNotifier {
    void statusChanged(Order order);
}
