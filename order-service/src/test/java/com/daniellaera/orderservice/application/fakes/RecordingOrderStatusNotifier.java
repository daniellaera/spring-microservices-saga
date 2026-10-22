package com.daniellaera.orderservice.application.fakes;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.out.OrderStatusNotifier;

import java.util.ArrayList;
import java.util.List;

public class RecordingOrderStatusNotifier implements OrderStatusNotifier {

    private final List<Order> notified = new ArrayList<>();

    @Override
    public void statusChanged(Order order) {
        notified.add(order);
    }

    public List<Order> notified() {
        return List.copyOf(notified);
    }
}
