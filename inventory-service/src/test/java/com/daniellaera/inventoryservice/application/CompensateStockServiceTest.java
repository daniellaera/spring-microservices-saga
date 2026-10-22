package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.application.fakes.InMemoryCompensationLedger;
import com.daniellaera.inventoryservice.application.fakes.InMemoryProductRepository;
import com.daniellaera.inventoryservice.application.fakes.InMemoryStockReservationRepository;
import com.daniellaera.inventoryservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.port.in.PaymentOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Ports the handlePaymentResult scenarios of the former InventoryConsumerTest. */
class CompensateStockServiceTest {

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final InMemoryStockReservationRepository reservations = new InMemoryStockReservationRepository();
    private final InMemoryCompensationLedger ledger = new InMemoryCompensationLedger();
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail();
    private final CompensateStockService service = new CompensateStockService(products, reservations, ledger, auditTrail);

    @BeforeEach
    void setUp() {
        products.given("MacBook Pro", 10, "1299.99");
        products.given("AirPods", 5, "249.99");
    }

    @Test
    void success_skipsCompensation() {
        reservations.given(1L, "MacBook Pro", 2);

        service.compensate(new PaymentOutcome(1L, "SUCCESS", "user@test.com"));

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(ledger.entries()).isEmpty();
    }

    @Test
    void anyStatusOtherThanFailed_skipsCompensation() {
        reservations.given(1L, "MacBook Pro", 2);

        service.compensate(new PaymentOutcome(1L, null, "user@test.com"));

        assertThat(products.saved()).isEmpty();
        assertThat(ledger.entries()).isEmpty();
    }

    @Test
    void failed_restoresStockAuditsAndRecordsTheLedger() {
        reservations.given(1L, "MacBook Pro", 2);

        service.compensate(new PaymentOutcome(1L, "FAILED", "user@test.com"));

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(12);
        assertThat(ledger.entries()).containsExactly(new InMemoryCompensationLedger.Entry(1L, "MacBook Pro", 2));
        assertThat(auditTrail.audits()).singleElement().satisfies(audit -> {
            assertThat(audit.eventType()).isEqualTo("STOCK_RESTORED");
            assertThat(audit.userEmail()).isEqualTo("user@test.com");
            assertThat(audit.product().quantity()).isEqualTo(12);
            assertThat(audit.payload()).isInstanceOfSatisfying(StockReservation.class,
                    r -> assertThat(r.productName()).isEqualTo("MacBook Pro"));
        });
    }

    @Test
    void failedMultiItem_restoresEveryItemAndRecordsFirstNameWithTotal() {
        reservations.given(6L, "MacBook Pro", 2);
        reservations.given(6L, "AirPods", 3);

        service.compensate(new PaymentOutcome(6L, "FAILED", "user@test.com"));

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(12);
        assertThat(products.quantityOf("AirPods")).isEqualTo(8);
        assertThat(ledger.entries()).containsExactly(new InMemoryCompensationLedger.Entry(6L, "MacBook Pro", 5));
        assertThat(auditTrail.audits()).hasSize(2);
    }

    @Test
    void alreadyCompensated_skipsIdempotently() {
        reservations.given(1L, "MacBook Pro", 2);
        ledger.record(1L, "MacBook Pro", 2);

        service.compensate(new PaymentOutcome(1L, "FAILED", "user@test.com"));

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(ledger.entries()).hasSize(1);
        assertThat(auditTrail.audits()).isEmpty();
    }

    @Test
    void failedAfterInventoryRejected_releasesNothing() {
        // Regression: inventory REJECTED the order, so no reservation was ever made; compensation must not
        // add the requested quantity to stock
        service.compensate(new PaymentOutcome(7L, "FAILED", "user@test.com"));

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(products.saved()).isEmpty();
        assertThat(ledger.entries()).isEmpty();
        assertThat(auditTrail.audits()).isEmpty();
    }

    @Test
    void failed_releasesTheRecordedQuantity() {
        // the payment event carries the requested items; only the recorded reservation decides what is released
        reservations.given(8L, "MacBook Pro", 3);

        service.compensate(new PaymentOutcome(8L, "FAILED", "user@test.com"));

        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(13);
        assertThat(ledger.entries()).singleElement().satisfies(entry -> assertThat(entry.quantity()).isEqualTo(3));
    }

    @Test
    void productDisappeared_compensatesTheRestAndRecordsOnlyWhatWasReleased() {
        reservations.given(11L, "Discontinued", 4);
        reservations.given(11L, "AirPods", 3);

        service.compensate(new PaymentOutcome(11L, "FAILED", "user@test.com"));

        assertThat(products.quantityOf("AirPods")).isEqualTo(8);
        assertThat(auditTrail.audits()).hasSize(1);
        // first reservation's name, total actually released (current behaviour)
        assertThat(ledger.entries()).containsExactly(new InMemoryCompensationLedger.Entry(11L, "Discontinued", 3));
    }
}
