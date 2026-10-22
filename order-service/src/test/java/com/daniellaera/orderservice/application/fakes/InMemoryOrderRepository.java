package com.daniellaera.orderservice.application.fakes;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.model.PageResult;
import com.daniellaera.orderservice.domain.port.out.OrderRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Behaves like the persistence adapter: assigns ids and createdAt to new orders, only updates the status of
 * existing ones, sorts pages newest first and hands out copies, never the stored instances.
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, Order> orders = new LinkedHashMap<>();
    private final List<Order> saved = new ArrayList<>();
    private long nextOrderId = 1;
    private long nextItemId = 1;
    private LocalDateTime clock = LocalDateTime.of(2025, 1, 1, 9, 0);

    @Override
    public Order save(Order order) {
        Order stored;
        if (order.id() == null) {
            clock = clock.plusMinutes(1);
            List<OrderItem> items = order.items().stream()
                    .map(i -> new OrderItem(nextItemId++, i.productName(), i.quantity(), i.price(), i.totalAmount()))
                    .toList();
            stored = Order.restore(nextOrderId++, order.userEmail(), order.paymentIntentId(), order.status(),
                    order.totalAmount(), clock, order.productName(), order.quantity(), order.price(), items);
        } else {
            Order existing = orders.get(order.id());
            if (existing == null) {
                throw new IllegalStateException("Unknown order " + order.id());
            }
            stored = Order.restore(existing.id(), existing.userEmail(), existing.paymentIntentId(), order.status(),
                    existing.totalAmount(), existing.createdAt(), existing.productName(), existing.quantity(),
                    existing.price(), existing.items());
        }
        orders.put(stored.id(), stored);
        saved.add(stored);
        return copy(stored);
    }

    @Override
    public Optional<Order> findById(long id) {
        return Optional.ofNullable(orders.get(id)).map(InMemoryOrderRepository::copy);
    }

    @Override
    public PageResult<Order> findByUserEmail(String userEmail, PageQuery page) {
        return page(orders.values().stream().filter(o -> userEmail.equals(o.userEmail())), page);
    }

    @Override
    public PageResult<Order> findAll(PageQuery page) {
        return page(orders.values().stream(), page);
    }

    /** Every order as stored by each save call, in call order. */
    public List<Order> saved() {
        return List.copyOf(saved);
    }

    // Same arithmetic as Spring Data's PageImpl
    private static PageResult<Order> page(Stream<Order> matching, PageQuery page) {
        List<Order> sorted = matching.sorted(Comparator.comparing(Order::createdAt).reversed()).toList();
        int from = Math.min(page.page() * page.size(), sorted.size());
        int to = Math.min(from + page.size(), sorted.size());
        int totalPages = (int) Math.ceil((double) sorted.size() / page.size());
        List<Order> content = sorted.subList(from, to).stream().map(InMemoryOrderRepository::copy).toList();
        return new PageResult<>(content, page.page(), totalPages, sorted.size(),
                page.page() + 1 < totalPages, page.page() > 0);
    }

    private static Order copy(Order o) {
        return Order.restore(o.id(), o.userEmail(), o.paymentIntentId(), o.status(), o.totalAmount(), o.createdAt(),
                o.productName(), o.quantity(), o.price(), o.items());
    }
}
