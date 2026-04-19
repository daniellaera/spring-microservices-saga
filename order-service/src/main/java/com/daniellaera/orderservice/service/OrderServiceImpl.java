package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.exception.ResourceNotFoundException;
import com.daniellaera.orderservice.model.Order;
import com.daniellaera.orderservice.producer.OrderProducer;
import com.daniellaera.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;

    @Override
    public OrderDTO createOrder(OrderRequest request) {
        Order order = new Order();
        order.setProductName(request.productName());
        order.setQuantity(request.quantity());
        order.setStatus(OrderStatus.PENDING);
        Order saved = orderRepository.save(order);
        orderProducer.sendOrder(saved);
        return new OrderDTO(saved.getId(), saved.getProductName(), saved.getQuantity(), saved.getStatus());
    }

    @Override
    public OrderDTO getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return new OrderDTO(order.getId(), order.getProductName(), order.getQuantity(), order.getStatus());
    }

    @Override
    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(o -> new OrderDTO(o.getId(), o.getProductName(), o.getQuantity(), o.getStatus()))
                .toList();
    }
}