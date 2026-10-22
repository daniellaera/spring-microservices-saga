package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.model.PageResult;
import com.daniellaera.orderservice.domain.port.in.ListOrdersQuery;
import com.daniellaera.orderservice.domain.port.out.OrderRepository;

public class ListOrdersService implements ListOrdersQuery {

    private final OrderRepository orderRepository;

    public ListOrdersService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public PageResult<Order> listOrdersOf(String userEmail, PageQuery page) {
        return orderRepository.findByUserEmail(userEmail, page);
    }

    @Override
    public PageResult<Order> listAllOrders(PageQuery page) {
        return orderRepository.findAll(page);
    }
}
