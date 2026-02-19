package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.model.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface OrderService {
    Order createOrder(OrderRequest request);
    Order getOrderById(Long id);
    List<Order> getAllOrders();
}