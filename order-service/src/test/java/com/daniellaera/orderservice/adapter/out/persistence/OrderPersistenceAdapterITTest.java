package com.daniellaera.orderservice.adapter.out.persistence;

import com.daniellaera.orderservice.TestcontainersConfiguration;
import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.model.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, OrderPersistenceAdapter.class})
class OrderPersistenceAdapterITTest {

    @Autowired
    private OrderPersistenceAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_newOrder_roundTripsWithItems() {
        Order saved = adapter.save(Order.place("user@test.com", "pi_123", List.of(
                OrderItem.of("iPhone 16", 2, new BigDecimal("999.99")),
                OrderItem.of("AirPods", 1, new BigDecimal("249.99")))));
        entityManager.flush();
        entityManager.clear();

        Order loaded = adapter.findById(saved.id()).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(loaded.userEmail()).isEqualTo("user@test.com");
        assertThat(loaded.paymentIntentId()).isEqualTo("pi_123");
        assertThat(loaded.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(loaded.totalAmount()).isEqualByComparingTo("2249.97");
        assertThat(loaded.productName()).isEqualTo("iPhone 16");
        assertThat(loaded.quantity()).isEqualTo(2);
        assertThat(loaded.price()).isEqualByComparingTo("999.99");
        assertThat(loaded.items()).extracting(OrderItem::productName).containsExactlyInAnyOrder("iPhone 16", "AirPods");
        assertThat(loaded.items()).allSatisfy(item -> assertThat(item.id()).isNotNull());
    }

    @Test
    void findById_unknownId_isEmpty() {
        assertThat(adapter.findById(999_999L)).isEmpty();
    }

    @Test
    void save_existingOrder_updatesStatusOnly() {
        Order saved = adapter.save(Order.place("user@test.com", "pi_123", List.of(
                OrderItem.of("iPhone 16", 2, new BigDecimal("999.99")))));
        entityManager.flush();
        entityManager.clear();
        // As stored: the column has microsecond precision, the JVM clock may have nanoseconds
        LocalDateTime storedCreatedAt = adapter.findById(saved.id()).orElseThrow().createdAt();

        // A stale or partial view of the order must not overwrite anything but the status
        Order changed = Order.restore(saved.id(), "someone@else.com", null, OrderStatus.PENDING,
                BigDecimal.ONE, null, "Other", 9, BigDecimal.TEN, List.of());
        changed.applyPaymentResult(true);
        adapter.save(changed);
        entityManager.flush();
        entityManager.clear();

        Order loaded = adapter.findById(saved.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(loaded.userEmail()).isEqualTo("user@test.com");
        assertThat(loaded.paymentIntentId()).isEqualTo("pi_123");
        assertThat(loaded.totalAmount()).isEqualByComparingTo("1999.98");
        assertThat(loaded.productName()).isEqualTo("iPhone 16");
        assertThat(loaded.items()).hasSize(1);
        assertThat(loaded.createdAt()).isEqualTo(storedCreatedAt);
    }

    @Test
    void findAll_pagesNewestFirst() {
        Long oldest = persistAt("alice@test.com", LocalDateTime.of(2025, 1, 1, 10, 0));
        Long newest = persistAt("bob@test.com", LocalDateTime.of(2025, 3, 1, 10, 0));
        Long middle = persistAt("alice@test.com", LocalDateTime.of(2025, 2, 1, 10, 0));

        PageResult<Order> first = adapter.findAll(new PageQuery(0, 2));
        PageResult<Order> second = adapter.findAll(new PageQuery(1, 2));

        assertThat(first.content()).extracting(Order::id).containsExactly(newest, middle);
        assertThat(first.page()).isEqualTo(0);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.hasPrevious()).isFalse();
        assertThat(second.content()).extracting(Order::id).containsExactly(oldest);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.hasPrevious()).isTrue();
    }

    @Test
    void findByUserEmail_filtersAndSortsNewestFirst() {
        Long older = persistAt("alice@test.com", LocalDateTime.of(2025, 1, 1, 10, 0));
        persistAt("bob@test.com", LocalDateTime.of(2025, 3, 1, 10, 0));
        Long newer = persistAt("alice@test.com", LocalDateTime.of(2025, 2, 1, 10, 0));

        PageResult<Order> result = adapter.findByUserEmail("alice@test.com", new PageQuery(0, 10));

        assertThat(result.content()).extracting(Order::id).containsExactly(newer, older);
        assertThat(result.content()).allSatisfy(o -> assertThat(o.userEmail()).isEqualTo("alice@test.com"));
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    // createdAt comes from @PrePersist, so pin it afterwards to make the sort deterministic
    private Long persistAt(String userEmail, LocalDateTime createdAt) {
        Long id = adapter.save(Order.place(userEmail, null, List.of(
                OrderItem.of("Item", 1, new BigDecimal("10.00"))))).id();
        entityManager.flush();
        entityManager.getEntityManager()
                .createNativeQuery("update orders set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
        return id;
    }
}
