package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.audit.AuditPublisher;
import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderEvent;
import com.daniellaera.orderservice.dto.OrderItemRequest;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.dto.PagedResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private AuditPublisher auditPublisher;

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
        order.setUserEmail("user@test.com");
    }

    @Test
    void getAllOrders_shouldReturnListOfDTOs() {
        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<OrderDTO> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().productName()).isEqualTo("MacBook Pro");
        assertThat(result.getFirst().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getFirst().userEmail()).isEqualTo("user@test.com");
        verify(orderRepository, times(1)).findAll();
    }

    @Test
    void getOrderById_shouldReturnDTO_whenExists() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderDTO result = orderService.getOrderById(1L, "user@test.com", "USER");

        assertThat(result.productName()).isEqualTo("MacBook Pro");
        assertThat(result.quantity()).isEqualTo(1);
    }

    @Test
    void getOrderById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(999L, "user@test.com", "USER"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void getOrderById_shouldThrowForbidden_whenNotOwnerAndNotAdmin() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(1L, "other@test.com", "USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void getOrderById_shouldReturnDTO_whenAdminAndNotOwner() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderDTO result = orderService.getOrderById(1L, "other@test.com", "ADMIN");

        assertThat(result.productName()).isEqualTo("MacBook Pro");
    }

    @Test
    void createOrder_shouldSaveAndPublishToOutbox() throws Exception {
        OrderRequest request = new OrderRequest("MacBook Pro", 1, BigDecimal.valueOf(1299.99), null, null);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(objectMapper.writeValueAsString(any(OrderEvent.class))).thenReturn("{\"orderId\":1}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        OrderDTO result = orderService.createOrder(request, "user@test.com");

        assertThat(result.productName()).isEqualTo("MacBook Pro");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        verifyNoInteractions(orderProducer);
        verify(auditPublisher).publish(eq("ORDER_CREATED"), any(), any(), any(), any());
    }

    @Test
    void createOrder_shouldSetUserEmail() throws Exception {
        OrderRequest request = new OrderRequest("iPhone", 1, BigDecimal.valueOf(799.99), null, null);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(objectMapper.writeValueAsString(any(OrderEvent.class))).thenReturn("{\"orderId\":1}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        OrderDTO result = orderService.createOrder(request, "user@test.com");

        assertThat(result.userEmail()).isEqualTo("user@test.com");
    }

    @Test
    void createOrder_withMultipleItems_shouldSumTotalAndPersistAllItems() throws Exception {
        OrderRequest request = new OrderRequest(null, null, null, null, List.of(
                new OrderItemRequest("iPhone 16", 2, BigDecimal.valueOf(999.99)),
                new OrderItemRequest("AirPods", 1, BigDecimal.valueOf(249.99)),
                new OrderItemRequest("Case", 3, BigDecimal.valueOf(19.99))
        ));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any(OrderEvent.class))).thenReturn("{\"orderId\":1}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        OrderDTO result = orderService.createOrder(request, "user@test.com");

        BigDecimal expectedTotal = BigDecimal.valueOf(999.99).multiply(BigDecimal.valueOf(2))
                .add(BigDecimal.valueOf(249.99))
                .add(BigDecimal.valueOf(19.99).multiply(BigDecimal.valueOf(3)));
        assertThat(result.totalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(result.items()).hasSize(3);
    }

    @Test
    void createOrder_shouldPersistOrderItemsOnSavedOrder() throws Exception {
        OrderRequest request = new OrderRequest(null, null, null, null, List.of(
                new OrderItemRequest("iPhone 16", 2, BigDecimal.valueOf(999.99)),
                new OrderItemRequest("AirPods", 1, BigDecimal.valueOf(249.99))
        ));
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any(OrderEvent.class))).thenReturn("{\"orderId\":1}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        orderService.createOrder(request, "user@test.com");

        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getItems()).hasSize(2);
        assertThat(savedOrder.getItems().get(0).getProductName()).isEqualTo("iPhone 16");
        assertThat(savedOrder.getItems().get(0).getTotalAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(1999.98));
    }

    @Test
    void createOrder_shouldSaveOutboxEventAfterOrderInSameCreateOrderCall() throws Exception {
        OrderRequest request = new OrderRequest("MacBook Pro", 1, BigDecimal.valueOf(1299.99), null, null);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(objectMapper.writeValueAsString(any(OrderEvent.class))).thenReturn("{\"orderId\":1}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        orderService.createOrder(request, "user@test.com");

        var inOrder = inOrder(orderRepository, outboxEventRepository);
        inOrder.verify(orderRepository).save(any(Order.class));
        inOrder.verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void createOrder_withNoItemsAndNoLegacyFields_shouldThrowBadRequest() {
        OrderRequest request = new OrderRequest(null, null, null, null, List.of());

        assertThatThrownBy(() -> orderService.createOrder(request, "user@test.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least one item");

        verifyNoInteractions(orderRepository, outboxEventRepository);
    }

    @Test
    void getMyOrders_shouldFilterByUserEmail() {
        when(orderRepository.findByUserEmail("user@test.com")).thenReturn(List.of(order));

        List<OrderDTO> result = orderService.getMyOrders("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().userEmail()).isEqualTo("user@test.com");
        assertThat(result.getFirst().productName()).isEqualTo("MacBook Pro");
        verify(orderRepository, times(1)).findByUserEmail("user@test.com");
    }

    @Test
    void getAllOrdersPaged_shouldReturnPagedResponse() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<OrderDTO> result = orderService.getAllOrdersPaged(0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.currentPage()).isEqualTo(0);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
        verify(orderRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void getMyOrdersPaged_shouldFilterByUserEmailAndReturnPagedResponse() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
        when(orderRepository.findByUserEmail(eq("user@test.com"), any(Pageable.class))).thenReturn(page);

        PagedResponse<OrderDTO> result = orderService.getMyOrdersPaged("user@test.com", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().userEmail()).isEqualTo("user@test.com");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.currentPage()).isEqualTo(0);
        verify(orderRepository, times(1)).findByUserEmail(eq("user@test.com"), any(Pageable.class));
    }
}
