package com.daniellaera.orderservice.adapter.in.web;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.model.PageResult;
import com.daniellaera.orderservice.domain.port.in.GetOrderQuery;
import com.daniellaera.orderservice.domain.port.in.ListOrdersQuery;
import com.daniellaera.orderservice.domain.port.in.PlaceOrderUseCase;
import com.daniellaera.orderservice.adapter.out.notification.SseEmitterRegistry;
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
    private PlaceOrderUseCase placeOrderUseCase;

    @Mock
    private GetOrderQuery getOrderQuery;

    @Mock
    private ListOrdersQuery listOrdersQuery;

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void createOrder_withUserEmailHeader_storesEmailOnOrder() throws Exception {
        Order order = order(1L, "MacBook Pro", BigDecimal.valueOf(999.99), OrderStatus.PENDING, "user@test.com");
        when(placeOrderUseCase.placeOrder(argThat(command -> "user@test.com".equals(command.userEmail()))))
                .thenReturn(order);

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
        List<Order> orders = List.of(
                order(1L, "MacBook Pro", BigDecimal.valueOf(999.99), OrderStatus.PENDING, "user@test.com")
        );
        PageResult<Order> paged = new PageResult<>(orders, 0, 1, 1L, false, false);
        when(listOrdersQuery.listOrdersOf("user@test.com", new PageQuery(0, 10))).thenReturn(paged);

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
        List<Order> orders = List.of(
                order(1L, "MacBook Pro", BigDecimal.valueOf(999.99), OrderStatus.PENDING, "user1@test.com"),
                order(2L, "iPhone", BigDecimal.valueOf(799.99), OrderStatus.CONFIRMED, "user2@test.com")
        );
        PageResult<Order> paged = new PageResult<>(orders, 0, 1, 2L, false, false);
        when(listOrdersQuery.listAllOrders(new PageQuery(0, 10))).thenReturn(paged);

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].userEmail").value("user1@test.com"))
                .andExpect(jsonPath("$.content[1].userEmail").value("user2@test.com"));
    }

    private static Order order(Long id, String productName, BigDecimal price, OrderStatus status, String userEmail) {
        return Order.restore(id, userEmail, null, status, price, null, productName, 1, price, List.of());
    }
}
