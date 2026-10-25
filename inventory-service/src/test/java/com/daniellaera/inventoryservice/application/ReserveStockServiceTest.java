package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.application.fakes.InMemoryProductRepository;
import com.daniellaera.inventoryservice.application.fakes.InMemoryStockReservationRepository;
import com.daniellaera.inventoryservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.inventoryservice.application.fakes.RecordingInventoryResultPublisher;
import com.daniellaera.inventoryservice.application.fakes.RecordingProductListCache;
import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Ports the consumeOrder scenarios of the former InventoryConsumerTest. Rollback itself needs a database and is
 * covered by the PostgreSQL integration test of the order listener; that a rolled-back deduction emits no audit is covered by
 * TransactionAwareKafkaAuditTrailTest.
 */
class ReserveStockServiceTest {

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final InMemoryStockReservationRepository reservations = new InMemoryStockReservationRepository();
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail();
    private final RecordingInventoryResultPublisher publisher = new RecordingInventoryResultPublisher();
    private final RecordingProductListCache productListCache = new RecordingProductListCache();
    private final ReserveStockService service = new ReserveStockService(reservations,
            new StockReserver(products, reservations, auditTrail, productListCache), publisher, 3);

    @BeforeEach
    void setUp() {
        products.given("MacBook Pro", 10, "1299.99");
        products.given("AirPods", 5, "249.99");
    }

    @Test
    void sufficientStock_deductsReservesAndPublishesApproved() {
        ReserveStockCommand command = command(1L, "pi_123", line("MacBook Pro", 2));

        assertThat(service.reserve(command)).contains(ReservationDecision.APPROVED);

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(8);
        assertThat(reservations.findByOrderId(1L))
                .extracting(StockReservation::orderId, StockReservation::productName, StockReservation::quantity)
                .containsExactly(tuple(1L, "MacBook Pro", 2));
        assertThat(auditTrail.audits()).singleElement().satisfies(audit -> {
            assertThat(audit.eventType()).isEqualTo("STOCK_DEDUCTED");
            assertThat(audit.userEmail()).isEqualTo("user@test.com");
            assertThat(audit.orderId()).isEqualTo(1L);
            assertThat(audit.product().quantity()).isEqualTo(8);
            assertThat(audit.payload()).isEqualTo(line("MacBook Pro", 2));
        });
        assertThat(publisher.published()).singleElement().satisfies(published -> {
            assertThat(published.decision()).isEqualTo(ReservationDecision.APPROVED);
            assertThat(published.command()).isSameAs(command);
            assertThat(published.command().paymentIntentId()).isEqualTo("pi_123");
        });
    }

    @Test
    void insufficientStock_publishesRejectedAndReservesNothing() {
        assertThat(service.reserve(command(2L, null, line("MacBook Pro", 20)))).contains(ReservationDecision.REJECTED);

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(products.saved()).isEmpty();
        assertThat(reservations.all()).isEmpty();
        assertThat(auditTrail.audits()).isEmpty();
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.decision()).isEqualTo(ReservationDecision.REJECTED));
    }

    @Test
    void approved_invalidatesTheProductList() {
        service.reserve(command(1L, "pi_123", line("MacBook Pro", 2), line("AirPods", 1)));

        assertThat(productListCache.invalidations()).isEqualTo(1);
    }

    @Test
    void rejectedUnknownOrRedelivered_leavesTheProductList() {
        service.reserve(command(2L, null, line("MacBook Pro", 20)));
        service.reserve(command(3L, null, line("Unknown Product", 1)));
        reservations.given(4L, "MacBook Pro", 1);
        service.reserve(command(4L, null, line("MacBook Pro", 1)));

        assertThat(productListCache.invalidations()).isZero();
    }

    @Test
    void exactlyTheStockOnHand_isApproved() {
        assertThat(service.reserve(command(3L, null, line("AirPods", 5)))).contains(ReservationDecision.APPROVED);

        assertThat(products.quantityOf("AirPods")).isZero();
    }

    @Test
    void unknownProduct_isRejectedAndPublishedWithNothingReserved() {
        ReserveStockCommand command = command(3L, "pi_unknown", line("Unknown Product", 1));

        assertThat(service.reserve(command)).contains(ReservationDecision.REJECTED);

        assertThat(products.saved()).isEmpty();
        assertThat(reservations.all()).isEmpty();
        assertThat(auditTrail.audits()).isEmpty();
        assertThat(publisher.published()).singleElement().satisfies(published -> {
            assertThat(published.decision()).isEqualTo(ReservationDecision.REJECTED);
            assertThat(published.command()).isSameAs(command);
        });
    }

    @Test
    void unknownProductAfterAKnownOne_touchesNothingAndIsRejected() {
        // products are resolved before any write
        assertThat(service.reserve(command(3L, null, line("MacBook Pro", 1), line("Unknown Product", 1))))
                .contains(ReservationDecision.REJECTED);

        assertThat(products.saved()).isEmpty();
        assertThat(reservations.all()).isEmpty();
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.decision()).isEqualTo(ReservationDecision.REJECTED));
    }

    @Test
    void multiItemAllInStock_deductsEveryItemAndPublishesApproved() {
        ReserveStockCommand command = command(4L, "pi_multi", line("MacBook Pro", 2), line("AirPods", 3));

        assertThat(service.reserve(command)).contains(ReservationDecision.APPROVED);

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(8);
        assertThat(products.quantityOf("AirPods")).isEqualTo(2);
        assertThat(reservations.findByOrderId(4L)).hasSize(2);
        assertThat(auditTrail.audits()).hasSize(2);
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.command().lines()).hasSize(2));
    }

    @Test
    void multiItem_locksEveryProductOnceInNameOrderBeforeTheStockCheck() {
        service.reserve(command(11L, null, line("MacBook Pro", 2), line("AirPods", 99), line("MacBook Pro", 1)));

        // rejected for AirPods, but only after both products were locked
        assertThat(products.locked()).containsExactly("AirPods", "MacBook Pro");
    }

    @Test
    void multiItemOneOutOfStock_rejectsWithoutDeductingAnyItem() {
        assertThat(service.reserve(command(5L, null, line("MacBook Pro", 2), line("AirPods", 99))))
                .contains(ReservationDecision.REJECTED);

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(products.quantityOf("AirPods")).isEqualTo(5);
        assertThat(products.saved()).isEmpty();
        assertThat(reservations.all()).isEmpty();
    }

    @Test
    void lockTimeoutThenLock_isTriedAgainAndApproved() {
        products.lockedOut("MacBook Pro", 2);

        assertThat(service.reserve(command(21L, "pi_21", line("MacBook Pro", 2)))).contains(ReservationDecision.APPROVED);

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(8);
        assertThat(reservations.findByOrderId(21L)).hasSize(1);
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.decision()).isEqualTo(ReservationDecision.APPROVED));
    }

    @Test
    void lockTimeoutOnEveryAttempt_isRejectedWithNothingReserved() {
        // AirPods is locked first (name order), MacBook Pro never gets its turn
        products.lockedOut("MacBook Pro", 3);

        assertThat(service.reserve(command(22L, "pi_22", line("AirPods", 1), line("MacBook Pro", 1))))
                .contains(ReservationDecision.REJECTED);

        assertThat(products.locked()).containsExactly("AirPods", "AirPods", "AirPods");
        assertThat(products.saved()).isEmpty();
        assertThat(reservations.all()).isEmpty();
        assertThat(auditTrail.audits()).isEmpty();
        assertThat(productListCache.invalidations()).isZero();
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.decision()).isEqualTo(ReservationDecision.REJECTED));
    }

    @Test
    void oneLockAttempt_rejectsOnTheFirstTimeout() {
        ReserveStockService singleAttempt = new ReserveStockService(reservations,
                new StockReserver(products, reservations, auditTrail, productListCache), publisher, 1);
        products.lockedOut("MacBook Pro", 1);

        assertThat(singleAttempt.reserve(command(23L, null, line("MacBook Pro", 1)))).contains(ReservationDecision.REJECTED);
        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(10);
    }

    @Test
    void lockAttemptsBelowOne_areRefused() {
        StockReserver reserver = new StockReserver(products, reservations, auditTrail, productListCache);

        assertThatThrownBy(() -> new ReserveStockService(reservations, reserver, publisher, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockAttempts must be at least 1, was 0");
    }

    @Test
    void redelivery_deductsOnceAndPublishesOnce() {
        ReserveStockCommand command = command(1L, "pi_123", line("MacBook Pro", 2));

        assertThat(service.reserve(command)).isPresent();
        assertThat(service.reserve(command)).isEmpty();

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(8);
        assertThat(reservations.findByOrderId(1L)).hasSize(1);
        assertThat(products.saved()).hasSize(1);
        assertThat(publisher.published()).hasSize(1);
    }

    @Test
    void reservationInsertFails_leavesStockUnchangedAndPublishesNothing() {
        // e.g. the unique constraint on (order_id, product_name) firing on a concurrent duplicate
        reservations.failOnInsert(1);

        assertThatThrownBy(() -> service.reserve(command(1L, "pi_123", line("MacBook Pro", 2))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(products.saved()).isEmpty();
        assertThat(auditTrail.audits()).isEmpty();
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void failureAfterFirstDeduction_propagatesAndPublishesNoResult() {
        // the MacBook deduction happened inside the transaction; the database rolls it back
        reservations.failOnInsert(2);

        assertThatThrownBy(() -> service.reserve(command(9L, null, line("MacBook Pro", 2), line("AirPods", 3))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(products.saved()).hasSize(1);
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void sameProductTwice_deductsCumulativelyAndHitsTheReservationConstraint() {
        // one product instance per name, like the persistence context; the second reservation insert violates
        // the (order_id, product_name) constraint, so the whole order rolls back in the database
        assertThatThrownBy(() -> service.reserve(command(10L, null, line("MacBook Pro", 2), line("MacBook Pro", 3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate reservation");

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(8);
        assertThat(publisher.published()).isEmpty();
    }

    private static ReserveStockCommand command(Long orderId, String paymentIntentId, OrderLine... lines) {
        OrderLine first = lines[0];
        return new ReserveStockCommand(orderId, first.productName(), first.quantity(), first.price(),
                first.totalAmount(), "user@test.com", paymentIntentId, List.of(lines));
    }

    private static OrderLine line(String productName, int quantity) {
        return new OrderLine(productName, quantity, BigDecimal.TEN, BigDecimal.TEN.multiply(BigDecimal.valueOf(quantity)));
    }
}
