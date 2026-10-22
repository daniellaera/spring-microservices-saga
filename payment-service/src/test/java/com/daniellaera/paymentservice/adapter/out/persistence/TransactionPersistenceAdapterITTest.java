package com.daniellaera.paymentservice.adapter.out.persistence;

import com.daniellaera.paymentservice.TestcontainersConfiguration;
import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TransactionPersistenceAdapter.class})
class TransactionPersistenceAdapterITTest {

    @Autowired
    private TransactionPersistenceAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_roundTripsEveryField() {
        PaymentTransaction saved = adapter.save(PaymentTransaction.record(9001L, new BigDecimal("49.90"), true));
        flushAndClear();

        PaymentTransaction loaded = adapter.findById(saved.id()).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(loaded.orderId()).isEqualTo(9001L);
        assertThat(loaded.totalAmount()).isEqualTo(new BigDecimal("49.90"));
        assertThat(loaded.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(loaded.createdAt()).isNotNull();
    }

    @Test
    void save_failedPaymentWithoutAmount() {
        PaymentTransaction saved = adapter.save(PaymentTransaction.record(9002L, null, false));
        flushAndClear();

        PaymentTransaction loaded = adapter.findById(saved.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(loaded.totalAmount()).isNull();
    }

    @Test
    void sameOrderTwice_isStoredTwice() {
        // no uniqueness per order: a redelivered inventory result records a second transaction (current behaviour)
        adapter.save(PaymentTransaction.record(9003L, BigDecimal.TEN, true));
        adapter.save(PaymentTransaction.record(9003L, BigDecimal.TEN, true));
        flushAndClear();

        assertThat(adapter.findAll()).filteredOn(t -> t.orderId() == 9003L).hasSize(2);
    }

    @Test
    void findById_unknownId_isEmpty() {
        assertThat(adapter.findById(999_999L)).isEmpty();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
