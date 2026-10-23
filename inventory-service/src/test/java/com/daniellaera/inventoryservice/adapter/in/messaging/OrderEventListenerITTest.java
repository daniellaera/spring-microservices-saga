package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import com.daniellaera.inventoryservice.adapter.out.messaging.InventoryResultEvent;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Runs the order consumer against a real PostgreSQL to verify the transaction boundary:
 * stock check, reservation insert and deduction either all commit or all roll back.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderEventListenerITTest {

    @Autowired
    private OrderEventListener inventoryConsumer;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private StockReservationJpaRepository stockReservationRepository;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        stockReservationRepository.deleteAll();
        productRepository.deleteAll();
        productRepository.save(product("MacBook Pro", 10, 1299.99));
        productRepository.save(product("AirPods", 5, 249.99));
    }

    @Test
    void reservationInsertFailure_rollsBackEarlierDeductionAndReservation() throws Exception {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 2, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98)),
                new OrderItemEvent("AirPods", 3, BigDecimal.valueOf(249.99), BigDecimal.valueOf(749.97))
        );
        String message = objectMapper.writeValueAsString(new OrderEvent(100L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(3349.95), "user@test.com", null, items));

        // first reservation (MacBook) is written for real, the second (AirPods) fails —
        // by then the MacBook deduction has already been applied inside the transaction.
        // (callRealMethod is unavailable on an interface-backed spy, so persist directly.)
        AtomicInteger saves = new AtomicInteger();
        doAnswer(inv -> {
            if (saves.incrementAndGet() == 2) {
                throw new DataIntegrityViolationException("simulated reservation insert failure");
            }
            StockReservationJpaEntity reservation = inv.getArgument(0);
            entityManager.persist(reservation);
            return reservation;
        }).when(stockReservationRepository).save(any(StockReservationJpaEntity.class));

        inventoryConsumer.consumeOrder(message);

        assertThat(quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(quantityOf("AirPods")).isEqualTo(5);
        assertThat(stockReservationRepository.findByOrderId(100L)).isEmpty();
        // nothing at all on Kafka: no inventory result, and no STOCK_DEDUCTED audit for the rolled-back deduction
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    void redelivery_deductsStockOnceAndPublishesOnce() throws Exception {
        String message = objectMapper.writeValueAsString(new OrderEvent(101L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98), "user@test.com", "pi_123", null));

        inventoryConsumer.consumeOrder(message);
        inventoryConsumer.consumeOrder(message);

        assertThat(quantityOf("MacBook Pro")).isEqualTo(8);
        assertThat(stockReservationRepository.findByOrderId(101L)).hasSize(1);
        verify(kafkaTemplate, times(1)).send(eq("inventory-topic"), anyString());
        // the after-commit hook fired exactly once (audit publisher is @Async, hence the timeout)
        verify(kafkaTemplate, timeout(2000).times(1)).send(eq("audit-topic"), contains("STOCK_DEDUCTED"));
    }

    @Test
    void unknownProduct_rollsBackAndPublishesRejected() throws Exception {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 2, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98)),
                new OrderItemEvent("Discontinued", 1, BigDecimal.TEN, BigDecimal.TEN)
        );
        String message = objectMapper.writeValueAsString(new OrderEvent(102L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2609.98), "user@test.com", "pi_102", items));

        inventoryConsumer.consumeOrder(message);

        assertThat(quantityOf("MacBook Pro")).isEqualTo(10);
        assertThat(stockReservationRepository.findByOrderId(102L)).isEmpty();
        // REJECTED with the legacy quantity 0, so payment fails it and order-service cancels the order
        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("inventory-topic"), result.capture());
        InventoryResultEvent published = objectMapper.readValue(result.getValue(), InventoryResultEvent.class);
        assertThat(published.orderId()).isEqualTo(102L);
        assertThat(published.status()).isEqualTo("REJECTED");
        assertThat(published.quantity()).isZero();
        assertThat(published.items()).hasSize(2);
        verify(kafkaTemplate, after(1000).never()).send(eq("audit-topic"), anyString());
    }

    private int quantityOf(String name) {
        return productRepository.findByName(name).orElseThrow().getQuantity();
    }

    private static ProductJpaEntity product(String name, int quantity, double price) {
        ProductJpaEntity p = new ProductJpaEntity();
        p.setName(name);
        p.setQuantity(quantity);
        p.setPrice(BigDecimal.valueOf(price));
        return p;
    }
}
