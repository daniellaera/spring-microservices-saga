package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.application.fakes.InMemoryOrderRepository;
import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderAccessDeniedException;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.OrderNotFoundException;
import com.daniellaera.orderservice.domain.port.in.Requester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetOrderServiceTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final GetOrderService service = new GetOrderService(repository);
    private long orderId;

    @BeforeEach
    void setUp() {
        orderId = repository.save(Order.place("user@test.com", null,
                List.of(OrderItem.of("MacBook Pro", 1, BigDecimal.valueOf(1299.99))))).id();
    }

    @Test
    void owner_getsOrder() {
        Order order = service.getOrder(orderId, new Requester("user@test.com", false));

        assertThat(order.productName()).isEqualTo("MacBook Pro");
        assertThat(order.quantity()).isEqualTo(1);
    }

    @Test
    void unknownOrder_throwsOrderNotFound() {
        assertThatThrownBy(() -> service.getOrder(999L, new Requester("user@test.com", false)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void otherUser_isDenied() {
        assertThatThrownBy(() -> service.getOrder(orderId, new Requester("other@test.com", false)))
                .isInstanceOf(OrderAccessDeniedException.class)
                .hasMessage("Access denied");
    }

    @Test
    void admin_getsAnyOrder() {
        Order order = service.getOrder(orderId, new Requester("other@test.com", true));

        assertThat(order.productName()).isEqualTo("MacBook Pro");
    }
}
