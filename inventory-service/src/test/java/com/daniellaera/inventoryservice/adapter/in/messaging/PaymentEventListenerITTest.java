package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import com.daniellaera.inventoryservice.adapter.out.persistence.CompensationLogJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.CompensationLogJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Runs compensation against a real PostgreSQL to verify its transaction boundary: the releases and the ledger
 * entry commit together, so a crash before the ledger entry leaves stock untouched and the redelivered payment
 * result restores it exactly once.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentEventListenerITTest {

    private static final String PAYMENT_FAILED = "{\"orderId\":200,\"status\":\"FAILED\",\"userEmail\":\"user@test.com\"}";

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private StockReservationJpaRepository stockReservationRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private CompensationLogJpaRepository compensationLogRepository;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        compensationLogRepository.deleteAll();
        stockReservationRepository.deleteAll();
        productRepository.deleteAll();
        // order 200 reserved 2 MacBook Pro and 3 AirPods: stock was 10 and 5 before the deduction
        productRepository.save(product("MacBook Pro", 8));
        productRepository.save(product("AirPods", 2));
        stockReservationRepository.save(reservation(200L, "MacBook Pro", 2));
        stockReservationRepository.save(reservation(200L, "AirPods", 3));
    }

    @Test
    void crashBeforeTheLedgerEntry_rollsBackEveryReleaseAndPropagates() {
        doThrow(new DataAccessResourceFailureException("simulated crash"))
                .when(compensationLogRepository).save(any(CompensationLogJpaEntity.class));

        assertThatThrownBy(() -> paymentEventListener.handlePaymentResult(PAYMENT_FAILED))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(quantityOf("MacBook Pro")).isEqualTo(8);
        assertThat(quantityOf("AirPods")).isEqualTo(2);
        assertThat(compensationLogRepository.existsByOrderId(200L)).isFalse();
        // no STOCK_RESTORED for releases that were rolled back (the audit publisher is @Async)
        verify(kafkaTemplate, after(1000).never()).send(eq("audit-topic"), anyString());
    }

    @Test
    void redeliveryAfterACrash_restoresExactlyOnce() throws Exception {
        doThrow(new DataAccessResourceFailureException("simulated crash"))
                .doAnswer(invocation -> {
                    // callRealMethod is unavailable on an interface-backed spy, so persist directly
                    CompensationLogJpaEntity entry = invocation.getArgument(0);
                    entityManager.persist(entry);
                    return entry;
                })
                .when(compensationLogRepository).save(any(CompensationLogJpaEntity.class));

        assertThatThrownBy(() -> paymentEventListener.handlePaymentResult(PAYMENT_FAILED))
                .isInstanceOf(DataAccessResourceFailureException.class);
        paymentEventListener.handlePaymentResult(PAYMENT_FAILED);
        paymentEventListener.handlePaymentResult(PAYMENT_FAILED);

        assertThat(quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(quantityOf("AirPods")).isEqualTo(5);
        assertThat(compensationLogRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getOrderId()).isEqualTo(200L);
            assertThat(entry.getQuantity()).isEqualTo(5);
        });
        // one STOCK_RESTORED per product, from the delivery that committed
        verify(kafkaTemplate, after(1000).times(2)).send(eq("audit-topic"), contains("STOCK_RESTORED"));
    }

    @Test
    void failure_withNothingGoingWrong_restoresAndRecordsTheLedger() throws Exception {
        paymentEventListener.handlePaymentResult(PAYMENT_FAILED);

        assertThat(quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(quantityOf("AirPods")).isEqualTo(5);
        assertThat(compensationLogRepository.existsByOrderId(200L)).isTrue();
        verify(kafkaTemplate, after(1000).times(2)).send(eq("audit-topic"), contains("STOCK_RESTORED"));
        verify(kafkaTemplate, never()).send(eq("inventory-topic"), anyString());
    }

    private int quantityOf(String name) {
        return productRepository.findByName(name).orElseThrow().getQuantity();
    }

    private static ProductJpaEntity product(String name, int quantity) {
        ProductJpaEntity product = new ProductJpaEntity();
        product.setName(name);
        product.setQuantity(quantity);
        product.setPrice(BigDecimal.TEN);
        return product;
    }

    private static StockReservationJpaEntity reservation(Long orderId, String productName, int quantity) {
        StockReservationJpaEntity reservation = new StockReservationJpaEntity();
        reservation.setOrderId(orderId);
        reservation.setProductName(productName);
        reservation.setQuantity(quantity);
        return reservation;
    }
}
