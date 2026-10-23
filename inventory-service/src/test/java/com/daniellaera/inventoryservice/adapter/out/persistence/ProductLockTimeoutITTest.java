package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.ProductLockTimeoutException;
import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.port.in.CompensateStockUseCase;
import com.daniellaera.inventoryservice.domain.port.in.ManageProductsUseCase;
import com.daniellaera.inventoryservice.domain.port.in.PaymentOutcome;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Another transaction holds the product's row lock (a plain JDBC connection, like a stuck stock change) while a
 * reservation, a compensation and a restock try to lock it. Each must give up after the lock timeout instead of
 * waiting forever, and leave nothing behind.
 */
@SpringBootTest(properties = "inventory.reservation.lock-attempts=2")
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// without a working lock timeout the lock waits forever: fail instead of hanging the build
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ProductLockTimeoutITTest {

    private static final String PRODUCT = "Locked Widget";
    private static final Duration LOCK_TIMEOUT = Duration.ofMillis(ProductPersistenceAdapter.LOCK_TIMEOUT_MS);

    @Autowired
    private ReserveStockUseCase reserveStock;

    @Autowired
    private CompensateStockUseCase compensateStock;

    @Autowired
    private ManageProductsUseCase manageProducts;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private StockReservationJpaRepository stockReservationRepository;

    @Autowired
    private CompensationLogJpaRepository compensationLogRepository;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private Connection lockHolder;

    @BeforeEach
    void setUp() {
        compensationLogRepository.deleteAll();
        stockReservationRepository.deleteAll();
        productRepository.deleteAll();
        ProductJpaEntity product = new ProductJpaEntity();
        product.setName(PRODUCT);
        product.setQuantity(5);
        product.setPrice(BigDecimal.TEN);
        productRepository.save(product);
    }

    @AfterEach
    void releaseTheLock() throws SQLException {
        if (lockHolder != null && !lockHolder.isClosed()) {
            lockHolder.rollback();
            lockHolder.close();
        }
    }

    @Test
    void reservation_givesUpAfterEveryAttemptTimesOut_andIsRejectedWithNothingReserved() throws Exception {
        holdTheLock();

        long start = System.nanoTime();
        ReservationDecision decision = reserveStock.reserve(orderOne(601)).orElseThrow();
        Duration waited = Duration.ofNanos(System.nanoTime() - start);

        assertThat(decision).isEqualTo(ReservationDecision.REJECTED);
        // two attempts, each waiting out the lock timeout, and not much more
        assertThat(waited).isBetween(LOCK_TIMEOUT.multipliedBy(2), LOCK_TIMEOUT.multipliedBy(2).plusSeconds(5));
        releaseTheLock();
        assertThat(stock()).isEqualTo(5);
        assertThat(stockReservationRepository.findAll()).isEmpty();
    }

    @Test
    void reservation_waitsForALockReleasedWithinTheTimeout_andIsApproved() throws Exception {
        holdTheLock();
        CompletableFuture<Void> release = CompletableFuture.runAsync(() -> {
            try {
                releaseTheLock();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));

        long start = System.nanoTime();
        ReservationDecision decision = reserveStock.reserve(orderOne(602)).orElseThrow();
        Duration waited = Duration.ofNanos(System.nanoTime() - start);
        release.get(5, TimeUnit.SECONDS);

        assertThat(decision).isEqualTo(ReservationDecision.APPROVED);
        assertThat(waited).isBetween(Duration.ofMillis(900), LOCK_TIMEOUT);
        assertThat(stock()).isEqualTo(4);
        assertThat(stockReservationRepository.findAll()).hasSize(1);
    }

    @Test
    void compensation_givesUpAfterTheTimeout_withNothingRecorded() throws Exception {
        StockReservationJpaEntity reservation = new StockReservationJpaEntity();
        reservation.setOrderId(603L);
        reservation.setProductName(PRODUCT);
        reservation.setQuantity(2);
        stockReservationRepository.save(reservation);
        holdTheLock();

        long start = System.nanoTime();
        assertThatThrownBy(() -> compensateStock.compensate(new PaymentOutcome(603L, "FAILED", "user@test.com")))
                .isInstanceOf(ProductLockTimeoutException.class);
        Duration waited = Duration.ofNanos(System.nanoTime() - start);

        assertThat(waited).isBetween(LOCK_TIMEOUT, LOCK_TIMEOUT.plusSeconds(5));
        releaseTheLock();
        assertThat(stock()).isEqualTo(5);
        assertThat(compensationLogRepository.findAll()).isEmpty();
        // the Kafka container redelivers the payment result: now that the lock is free, it compensates once
        compensateStock.compensate(new PaymentOutcome(603L, "FAILED", "user@test.com"));
        assertThat(stock()).isEqualTo(7);
    }

    @Test
    void restock_givesUpAfterTheTimeout_andChangesNothing() throws Exception {
        long productId = productRepository.findByName(PRODUCT).orElseThrow().getId();
        holdTheLock();

        long start = System.nanoTime();
        assertThatThrownBy(() -> manageProducts.restock(productId, 10))
                .isInstanceOf(ProductLockTimeoutException.class)
                .hasMessage("Product #" + productId + " is locked by another stock change");
        Duration waited = Duration.ofNanos(System.nanoTime() - start);

        assertThat(waited).isBetween(LOCK_TIMEOUT, LOCK_TIMEOUT.plusSeconds(5));
        releaseTheLock();
        assertThat(stock()).isEqualTo(5);
    }

    /** Locks the product row in a transaction of its own, which stays open until releaseTheLock. */
    private void holdTheLock() throws SQLException {
        lockHolder = dataSource.getConnection();
        lockHolder.setAutoCommit(false);
        try (PreparedStatement select = lockHolder.prepareStatement("select id from product where name = ? for update")) {
            select.setString(1, PRODUCT);
            assertThat(select.executeQuery().next()).isTrue();
        }
    }

    private static ReserveStockCommand orderOne(long orderId) {
        OrderLine line = new OrderLine(PRODUCT, 1, BigDecimal.TEN, BigDecimal.TEN);
        return new ReserveStockCommand(orderId, PRODUCT, 1, BigDecimal.TEN, BigDecimal.TEN, "user@test.com",
                "pi_" + orderId, List.of(line));
    }

    private int stock() {
        return productRepository.findByName(PRODUCT).orElseThrow().getQuantity();
    }
}
