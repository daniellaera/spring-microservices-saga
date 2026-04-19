package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.exception.ResourceNotFoundException;
import com.daniellaera.orderservice.model.Order;
import com.daniellaera.orderservice.producer.OrderProducer;
import com.daniellaera.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProducer orderProducer;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setProductName("MacBook Pro");
        order.setQuantity(1);
        order.setStatus(OrderStatus.PENDING);
    }

    @Test
    void getAllOrders_shouldReturnListOfDTOs() {
        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<OrderDTO> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().productName()).isEqualTo("MacBook Pro");
        assertThat(result.getFirst().status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, times(1)).findAll();
    }

    @Test
    void getOrderById_shouldReturnDTO_whenExists() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderDTO result = orderService.getOrderById(1L);

        assertThat(result.productName()).isEqualTo("MacBook Pro");
        assertThat(result.quantity()).isEqualTo(1);
    }

    @Test
    void getOrderById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void createOrder_shouldSaveAndPublishToKafka() {
        OrderRequest request = new OrderRequest("MacBook Pro", 1);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderDTO result = orderService.createOrder(request);

        assertThat(result.productName()).isEqualTo("MacBook Pro");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderProducer, times(1)).sendOrder(any(Order.class));
    }
}