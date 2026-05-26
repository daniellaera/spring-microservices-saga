package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.dto.PagedResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface OrderService {
    OrderDTO createOrder(OrderRequest request, String userEmail);
    OrderDTO getOrderById(Long id);
    List<OrderDTO> getAllOrders();
    List<OrderDTO> getMyOrders(String userEmail);
    PagedResponse<OrderDTO> getMyOrdersPaged(String userEmail, int page, int size);
    PagedResponse<OrderDTO> getAllOrdersPaged(int page, int size);
}