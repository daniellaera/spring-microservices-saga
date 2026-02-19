package com.daniellaera.orderservice.service.impl;

import com.daniellaera.orderservice.producer.OrderProducer;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.exception.ResourceNotFoundException;
import com.daniellaera.orderservice.model.Order;
import com.daniellaera.orderservice.repository.OrderRepository;
import com.daniellaera.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;

    @Override
    public Order createOrder(OrderRequest request) {
        Order order = new Order();
        order.setProductName(request.productName());
        order.setQuantity(request.quantity());
        order.setStatus(OrderStatus.PENDING);

        Order saved = orderRepository.save(order);
        orderProducer.sendOrder(saved);
        return saved;
    }

    @Override
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}