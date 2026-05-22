package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderEvent;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.exception.ResourceNotFoundException;
import com.daniellaera.orderservice.model.Order;
import com.daniellaera.orderservice.model.OutboxEvent;
import com.daniellaera.orderservice.producer.OrderProducer;
import com.daniellaera.orderservice.repository.OrderRepository;
import com.daniellaera.orderservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OrderProducer orderProducer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setProductName("MacBook Pro");
        order.setQuantity(1);
        order.setPrice(BigDecimal.valueOf(1299.99));
        order.setTotalAmount(BigDecimal.valueOf(1299.99));
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
    void createOrder_shouldSaveAndPublishToOutbox() throws Exception {
        OrderRequest request = new OrderRequest("MacBook Pro", 1, BigDecimal.valueOf(1299.99));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(objectMapper.writeValueAsString(any(OrderEvent.class))).thenReturn("{\"orderId\":1}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        OrderDTO result = orderService.createOrder(request);

        assertThat(result.productName()).isEqualTo("MacBook Pro");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        verifyNoInteractions(orderProducer);
    }
}
