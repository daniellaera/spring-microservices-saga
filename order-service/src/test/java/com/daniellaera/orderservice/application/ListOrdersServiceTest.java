package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.application.fakes.InMemoryOrderRepository;
import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.model.PageResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListOrdersServiceTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final ListOrdersService service = new ListOrdersService(repository);

    @Test
    void listAllOrders_returnsEveryOrder() {
        save("user@test.com", "MacBook Pro");

        PageResult<Order> result = service.listAllOrders(new PageQuery(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().productName()).isEqualTo("MacBook Pro");
        assertThat(result.content().getFirst().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.content().getFirst().userEmail()).isEqualTo("user@test.com");
    }

    @Test
    void listAllOrders_returnsPageMetadata() {
        save("user@test.com", "MacBook Pro");

        PageResult<Order> result = service.listAllOrders(new PageQuery(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    void listOrdersOf_returnsOnlyThatUsersOrders() {
        save("user@test.com", "MacBook Pro");
        save("other@test.com", "iPad");

        PageResult<Order> result = service.listOrdersOf("user@test.com", new PageQuery(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().userEmail()).isEqualTo("user@test.com");
        assertThat(result.content().getFirst().productName()).isEqualTo("MacBook Pro");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
    }

    @Test
    void listOrdersOf_pagesNewestFirst() {
        Order first = save("user@test.com", "First");
        Order second = save("user@test.com", "Second");
        Order third = save("user@test.com", "Third");

        PageResult<Order> page0 = service.listOrdersOf("user@test.com", new PageQuery(0, 2));
        PageResult<Order> page1 = service.listOrdersOf("user@test.com", new PageQuery(1, 2));

        assertThat(page0.content()).extracting(Order::id).containsExactly(third.id(), second.id());
        assertThat(page0.hasNext()).isTrue();
        assertThat(page1.content()).extracting(Order::id).containsExactly(first.id());
        assertThat(page1.hasPrevious()).isTrue();
        assertThat(page1.totalPages()).isEqualTo(2);
    }

    private Order save(String userEmail, String productName) {
        return repository.save(Order.place(userEmail, null, List.of(OrderItem.of(productName, 1, BigDecimal.TEN))));
    }
}
