package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.port.in.CompensateStockUseCase;
import com.daniellaera.inventoryservice.domain.port.in.ManageProductsUseCase;
import com.daniellaera.inventoryservice.domain.port.in.PaymentOutcome;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs reservations, compensations and restocks of the same product in concurrent transactions against
 * PostgreSQL. Each stock change reads and writes the product's quantity; without the row lock, two of them
 * read the same value and one update is lost.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConcurrentStockChangesITTest {

    private static final String PRODUCT = "Contended Widget";

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

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        compensationLogRepository.deleteAll();
        stockReservationRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void tenOrdersOfOne_againstFiveInStock_approveExactlyFive() throws Exception {
        givenStock(5);

        List<ReservationDecision> decisions = runConcurrently(
                LongStream.rangeClosed(301, 310).<Callable<ReservationDecision>>mapToObj(this::orderOne).toList());

        assertThat(decisions).filteredOn(ReservationDecision.APPROVED::equals).hasSize(5);
        assertThat(decisions).filteredOn(ReservationDecision.REJECTED::equals).hasSize(5);
        assertThat(stock()).isZero();
        assertThat(stockReservationRepository.findAll()).hasSize(5);
    }

    @Test
    void restocksRacingOrders_loseNoUpdate() throws Exception {
        givenStock(5);
        long productId = productRepository.findByName(PRODUCT).orElseThrow().getId();
        List<Callable<ReservationDecision>> tasks = new ArrayList<>(
                LongStream.rangeClosed(401, 410).<Callable<ReservationDecision>>mapToObj(this::orderOne).toList());
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                manageProducts.restock(productId, 1);
                return null;
            });
        }

        List<ReservationDecision> decisions = runConcurrently(tasks);

        long approved = decisions.stream().filter(ReservationDecision.APPROVED::equals).count();
        assertThat(approved).isBetween(5L, 10L);
        // 5 in stock + 5 restocked - what was approved: nothing lost, nothing created
        assertThat(stock()).isEqualTo(5 + 5 - (int) approved);
        assertThat(stockReservationRepository.findAll()).hasSize((int) approved);
    }

    @Test
    void compensationsRacingOrders_loseNoUpdate() throws Exception {
        // orders 501-505 each reserved 1, which took the stock down to 0; their payments now fail
        givenStock(0);
        for (long orderId = 501; orderId <= 505; orderId++) {
            StockReservationJpaEntity reservation = new StockReservationJpaEntity();
            reservation.setOrderId(orderId);
            reservation.setProductName(PRODUCT);
            reservation.setQuantity(1);
            stockReservationRepository.save(reservation);
        }
        List<Callable<ReservationDecision>> tasks = new ArrayList<>(
                LongStream.rangeClosed(511, 515).<Callable<ReservationDecision>>mapToObj(this::orderOne).toList());
        for (long orderId = 501; orderId <= 505; orderId++) {
            long failedOrder = orderId;
            tasks.add(() -> {
                compensateStock.compensate(new PaymentOutcome(failedOrder, "FAILED", "user@test.com"));
                return null;
            });
        }

        List<ReservationDecision> decisions = runConcurrently(tasks);

        long approved = decisions.stream().filter(ReservationDecision.APPROVED::equals).count();
        // 5 released - what was approved
        assertThat(stock()).isEqualTo(5 - (int) approved);
        assertThat(compensationLogRepository.findAll()).hasSize(5);
    }

    private Callable<ReservationDecision> orderOne(long orderId) {
        OrderLine line = new OrderLine(PRODUCT, 1, BigDecimal.TEN, BigDecimal.TEN);
        ReserveStockCommand command = new ReserveStockCommand(orderId, PRODUCT, 1, BigDecimal.TEN, BigDecimal.TEN,
                "user@test.com", "pi_" + orderId, List.of(line));
        return () -> reserveStock.reserve(command).orElseThrow();
    }

    /** Starts every task at the same instant and returns their non-null results. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                Optional.ofNullable(future.get(30, TimeUnit.SECONDS)).ifPresent(results::add);
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void givenStock(int quantity) {
        ProductJpaEntity product = new ProductJpaEntity();
        product.setName(PRODUCT);
        product.setQuantity(quantity);
        product.setPrice(BigDecimal.TEN);
        productRepository.save(product);
    }

    private int stock() {
        return productRepository.findByName(PRODUCT).orElseThrow().getQuantity();
    }
}
