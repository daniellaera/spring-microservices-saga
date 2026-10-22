package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.NoCachingConfiguration;
import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Runs the persistence adapters against PostgreSQL with the Flyway schema (V3 seeds a few products, so the
 * tests use their own names and order ids).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, NoCachingConfiguration.class, ProductPersistenceAdapter.class,
        StockReservationPersistenceAdapter.class, CompensationLedgerPersistenceAdapter.class})
class PersistenceAdaptersITTest {

    @Autowired
    private ProductPersistenceAdapter products;

    @Autowired
    private StockReservationPersistenceAdapter reservations;

    @Autowired
    private CompensationLedgerPersistenceAdapter ledger;

    @Autowired
    private CompensationLogJpaRepository compensationLogJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void product_newProduct_roundTrips() {
        Product saved = products.save(Product.create("IT Widget", 7, new BigDecimal("12.50")));
        flushAndClear();

        Product loaded = products.findById(saved.id()).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(loaded.name()).isEqualTo("IT Widget");
        assertThat(loaded.quantity()).isEqualTo(7);
        assertThat(loaded.price()).isEqualByComparingTo("12.50");
        assertThat(products.findByName("IT Widget")).get().extracting(Product::id).isEqualTo(saved.id());
        assertThat(products.existsByName("IT Widget")).isTrue();
        assertThat(products.existsByName("No Such Widget")).isFalse();
        assertThat(products.findAll()).extracting(Product::name).contains("IT Widget");
    }

    @Test
    void product_unknownIdOrName_isEmpty() {
        assertThat(products.findById(999_999L)).isEmpty();
        assertThat(products.findByName("No Such Widget")).isEmpty();
    }

    @Test
    void product_existingProduct_updatesQuantityOnly() {
        Product saved = products.save(Product.create("IT Widget", 7, new BigDecimal("12.50")));
        flushAndClear();

        Product loaded = products.findByName("IT Widget").orElseThrow();
        loaded.deduct(3);
        Product updated = products.save(loaded);
        flushAndClear();

        Product reloaded = products.findById(saved.id()).orElseThrow();
        assertThat(updated.quantity()).isEqualTo(4);
        assertThat(reloaded.quantity()).isEqualTo(4);
        assertThat(reloaded.name()).isEqualTo("IT Widget");
        assertThat(reloaded.price()).isEqualByComparingTo("12.50");
        assertThat(reloaded.createdAt()).isEqualTo(saved.createdAt());
    }

    @Test
    void reservations_areFoundByOrder() {
        StockReservation saved = reservations.save(StockReservation.of(9001L, "MacBook Pro", 2));
        reservations.save(StockReservation.of(9001L, "iPad Pro", 1));
        reservations.save(StockReservation.of(9002L, "MacBook Pro", 5));
        flushAndClear();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.reservedAt()).isNotNull();
        assertThat(reservations.findByOrderId(9001L))
                .extracting(StockReservation::productName, StockReservation::quantity)
                .containsExactlyInAnyOrder(tuple("MacBook Pro", 2),
                        tuple("iPad Pro", 1));
        assertThat(reservations.findByOrderId(9003L)).isEmpty();
        assertThat(reservations.existsByOrderId(9002L)).isTrue();
        assertThat(reservations.existsByOrderId(9003L)).isFalse();
    }

    @Test
    void reservations_sameOrderAndProductTwice_violatesTheUniqueConstraint() {
        reservations.save(StockReservation.of(9004L, "MacBook Pro", 2));
        reservations.save(StockReservation.of(9004L, "MacBook Pro", 3));

        assertThatThrownBy(entityManager::flush).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void ledger_recordsOneEntryPerOrder() {
        assertThat(ledger.isCompensated(9005L)).isFalse();

        ledger.record(9005L, "MacBook Pro", 3);
        flushAndClear();

        assertThat(ledger.isCompensated(9005L)).isTrue();
        assertThat(compensationLogJpaRepository.findAll())
                .filteredOn(entry -> entry.getOrderId() == 9005L)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getProductName()).isEqualTo("MacBook Pro");
                    assertThat(entry.getQuantity()).isEqualTo(3);
                    assertThat(entry.getCompensatedAt()).isNotNull();
                });
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
