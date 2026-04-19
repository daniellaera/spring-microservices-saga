package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface OrderService {
    OrderDTO createOrder(OrderRequest request);
    OrderDTO getOrderById(Long id);
    List<OrderDTO> getAllOrders();
}