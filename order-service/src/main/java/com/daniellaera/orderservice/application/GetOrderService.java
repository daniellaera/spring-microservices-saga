package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderNotFoundException;
import com.daniellaera.orderservice.domain.port.in.GetOrderQuery;
import com.daniellaera.orderservice.domain.port.in.Requester;
import com.daniellaera.orderservice.domain.port.out.OrderRepository;

public class GetOrderService implements GetOrderQuery {

    private final OrderRepository orderRepository;

    public GetOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Order getOrder(long orderId, Requester requester) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.ensureReadableBy(requester.email(), requester.admin());
        return order;
    }
}
