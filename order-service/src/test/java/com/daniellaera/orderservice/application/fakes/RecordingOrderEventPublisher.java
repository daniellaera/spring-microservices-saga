package com.daniellaera.orderservice.application.fakes;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.out.OrderEventPublisher;

import java.util.ArrayList;
import java.util.List;

public class RecordingOrderEventPublisher implements OrderEventPublisher {

    private final List<Order> placed = new ArrayList<>();

    @Override
    public void orderPlaced(Order order) {
        placed.add(order);
    }

    public List<Order> placed() {
        return List.copyOf(placed);
    }
}
