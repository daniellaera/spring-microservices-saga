package com.daniellaera.orderservice.controller;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.dto.PagedResponse;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.service.OrderService;
import com.daniellaera.orderservice.sse.SseEmitterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @InjectMocks
    private OrderController orderController;

    @Mock
    private OrderService orderService;

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void createOrder_withUserEmailHeader_storesEmailOnOrder() throws Exception {
        OrderDTO dto = new OrderDTO(1L, "MacBook Pro", 1,
                BigDecimal.valueOf(999.99), BigDecimal.valueOf(999.99),
                OrderStatus.PENDING, "user@test.com", null);
        when(orderService.createOrder(any(OrderRequest.class), eq("user@test.com"))).thenReturn(dto);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"MacBook Pro\",\"quantity\":1,\"price\":999.99}")
                        .header("X-User-Email", "user@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("user@test.com"))
                .andExpect(jsonPath("$.productName").value("MacBook Pro"));
    }

    @Test
    void getMyOrders_returnsOrdersForEmail() throws Exception {
        List<OrderDTO> orders = List.of(
                new OrderDTO(1L, "MacBook Pro", 1,
                        BigDecimal.valueOf(999.99), BigDecimal.valueOf(999.99),
                        OrderStatus.PENDING, "user@test.com", null)
        );
        PagedResponse<OrderDTO> paged = new PagedResponse<>(orders, 0, 1, 1L, false, false);
        when(orderService.getMyOrdersPaged("user@test.com", 0, 10)).thenReturn(paged);

        mockMvc.perform(get("/orders/my")
                        .header("X-User-Email", "user@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userEmail").value("user@test.com"))
                .andExpect(jsonPath("$.content[0].productName").value("MacBook Pro"));
    }

    @Test
    void getMyOrders_withoutHeader_returns401() throws Exception {
        mockMvc.perform(get("/orders/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllOrders_returnsAllOrders() throws Exception {
        List<OrderDTO> orders = List.of(
                new OrderDTO(1L, "MacBook Pro", 1,
                        BigDecimal.valueOf(999.99), BigDecimal.valueOf(999.99),
                        OrderStatus.PENDING, "user1@test.com", null),
                new OrderDTO(2L, "iPhone", 1,
                        BigDecimal.valueOf(799.99), BigDecimal.valueOf(799.99),
                        OrderStatus.CONFIRMED, "user2@test.com", null)
        );
        PagedResponse<OrderDTO> paged = new PagedResponse<>(orders, 0, 1, 2L, false, false);
        when(orderService.getAllOrdersPaged(0, 10)).thenReturn(paged);

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].userEmail").value("user1@test.com"))
                .andExpect(jsonPath("$.content[1].userEmail").value("user2@test.com"));
    }
}
