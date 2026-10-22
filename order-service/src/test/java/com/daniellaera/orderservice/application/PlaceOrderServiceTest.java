package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.application.fakes.InMemoryOrderRepository;
import com.daniellaera.orderservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.orderservice.application.fakes.RecordingOrderEventPublisher;
import com.daniellaera.orderservice.domain.model.EmptyOrderException;
import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.port.in.PlaceOrderCommand;
import com.daniellaera.orderservice.domain.port.in.PlaceOrderCommand.Line;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceOrderServiceTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final RecordingOrderEventPublisher eventPublisher = new RecordingOrderEventPublisher();
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail();
    private final PlaceOrderService service = new PlaceOrderService(repository, eventPublisher, auditTrail);

    @Test
    void placeOrder_savesPendingOrderAndPublishesToOutboxAndAudit() {
        Order placed = service.placeOrder(command("user@test.com", new Line("MacBook Pro", 1, new BigDecimal("1299.99"))));

        assertThat(placed.productName()).isEqualTo("MacBook Pro");
        assertThat(placed.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(repository.saved()).hasSize(1);
        assertThat(eventPublisher.placed()).hasSize(1);
        assertThat(auditTrail.placed()).hasSize(1);
    }

    @Test
    void placeOrder_keepsUserEmail() {
        Order placed = service.placeOrder(command("user@test.com", new Line("iPhone", 1, new BigDecimal("799.99"))));

        assertThat(placed.userEmail()).isEqualTo("user@test.com");
        assertThat(repository.findById(placed.id()).orElseThrow().userEmail()).isEqualTo("user@test.com");
    }

    @Test
    void placeOrder_multipleItems_sumsTotalAndKeepsAllItems() {
        Order placed = service.placeOrder(command("user@test.com",
                new Line("iPhone 16", 2, BigDecimal.valueOf(999.99)),
                new Line("AirPods", 1, BigDecimal.valueOf(249.99)),
                new Line("Case", 3, BigDecimal.valueOf(19.99))));

        BigDecimal expectedTotal = BigDecimal.valueOf(999.99).multiply(BigDecimal.valueOf(2))
                .add(BigDecimal.valueOf(249.99))
                .add(BigDecimal.valueOf(19.99).multiply(BigDecimal.valueOf(3)));
        assertThat(placed.totalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(placed.items()).hasSize(3);
    }

    @Test
    void placeOrder_persistsItemsWithLineTotals() {
        service.placeOrder(command("user@test.com",
                new Line("iPhone 16", 2, BigDecimal.valueOf(999.99)),
                new Line("AirPods", 1, BigDecimal.valueOf(249.99))));

        Order saved = repository.saved().get(0);
        assertThat(saved.items()).hasSize(2);
        assertThat(saved.items().get(0).productName()).isEqualTo("iPhone 16");
        assertThat(saved.items().get(0).totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1999.98));
        assertThat(saved.items()).extracting(OrderItem::id).doesNotContainNull();
    }

    @Test
    void placeOrder_publishesAndAuditsTheSavedOrder() {
        Order placed = service.placeOrder(command("user@test.com", new Line("MacBook Pro", 1, new BigDecimal("1299.99"))));

        // An id is only assigned by the repository, so both happened after the save
        assertThat(placed.id()).isNotNull();
        assertThat(eventPublisher.placed()).extracting(Order::id).containsExactly(placed.id());
        assertThat(auditTrail.placed()).extracting(Order::id).containsExactly(placed.id());
    }

    @Test
    void placeOrder_withoutLines_throwsEmptyOrderAndHasNoSideEffects() {
        assertThatThrownBy(() -> service.placeOrder(command("user@test.com")))
                .isInstanceOf(EmptyOrderException.class)
                .hasMessageContaining("at least one item");

        assertThat(repository.saved()).isEmpty();
        assertThat(repository.findAll(new PageQuery(0, 10)).totalElements()).isZero();
        assertThat(eventPublisher.placed()).isEmpty();
        assertThat(auditTrail.placed()).isEmpty();
    }

    @Test
    void placeOrder_withoutPaymentIntent_isStillAccepted() {
        Order placed = service.placeOrder(command("user@test.com", new Line("MacBook Pro", 1, new BigDecimal("1299.99"))));

        assertThat(placed.paymentIntentId()).isNull();
        assertThat(placed.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void placeOrder_runsInOneTransaction() throws NoSuchMethodException {
        assertThat(PlaceOrderService.class.getMethod("placeOrder", PlaceOrderCommand.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static PlaceOrderCommand command(String userEmail, Line... lines) {
        return new PlaceOrderCommand(userEmail, null, List.of(lines));
    }
}
