package com.daniellaera.paymentservice.adapter.out.persistence;

import com.daniellaera.paymentservice.TestcontainersConfiguration;
import com.daniellaera.paymentservice.domain.model.PaymentIntentAlreadyUsedException;
import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.model.TransactionAlreadyRecordedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void refund_roundTrips() {
        PaymentTransaction saved = adapter.save(new PaymentTransaction(null, 9006L, new BigDecimal("49.90"),
                PaymentStatus.FAILED, "pi_it_9006", "re_it_9006", null));
        flushAndClear();

        PaymentTransaction loaded = adapter.findById(saved.id()).orElseThrow();

        assertThat(loaded.refundId()).isEqualTo("re_it_9006");
        assertThat(loaded.paymentIntentId()).isEqualTo("pi_it_9006");
        assertThat(loaded.status()).isEqualTo(PaymentStatus.FAILED);
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // as in prod: no surrounding transaction to abort
    void sameOrderTwice_isRejected() {
        // was sameOrderTwice_isStoredTwice: uk_transaction_order_id now allows one transaction per order
        adapter.save(PaymentTransaction.record(9003L, BigDecimal.TEN, true));

        assertThatThrownBy(() -> adapter.save(PaymentTransaction.record(9003L, BigDecimal.TEN, false)))
                .isInstanceOf(TransactionAlreadyRecordedException.class)
                .hasMessage("A transaction is already recorded for order 9003");

        assertThat(adapter.findAll()).filteredOn(t -> t.orderId() == 9003L).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(PaymentStatus.SUCCESS));
    }

    @Test
    void findByOrderId_findsTheOrdersTransaction() {
        PaymentTransaction saved = adapter.save(PaymentTransaction.record(9004L, BigDecimal.ONE, false));
        flushAndClear();

        assertThat(adapter.findByOrderId(9004L)).hasValueSatisfying(t -> {
            assertThat(t.id()).isEqualTo(saved.id());
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
        });
        assertThat(adapter.findByOrderId(424_242L)).isEmpty();
    }

    @Test
    void payingIntent_roundTripsAndIsFoundByIntent() {
        PaymentTransaction saved = adapter.save(PaymentTransaction.paid(9005L, BigDecimal.TEN, "pi_it_9005"));
        flushAndClear();

        assertThat(adapter.findById(saved.id()).orElseThrow().paymentIntentId()).isEqualTo("pi_it_9005");
        assertThat(adapter.findByPaymentIntentId("pi_it_9005"))
                .hasValueSatisfying(t -> assertThat(t.orderId()).isEqualTo(9005L));
        assertThat(adapter.findByPaymentIntentId("pi_it_unknown")).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // as in prod: no surrounding transaction to abort
    void sameIntentForAnotherOrder_isRejected() {
        adapter.save(PaymentTransaction.paid(9006L, BigDecimal.TEN, "pi_it_shared"));

        assertThatThrownBy(() -> adapter.save(PaymentTransaction.paid(9007L, BigDecimal.TEN, "pi_it_shared")))
                .isInstanceOf(PaymentIntentAlreadyUsedException.class)
                .hasMessage("Payment intent pi_it_shared already paid for another order");
        assertThat(adapter.findByOrderId(9007L)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failuresWithoutIntent_neverCollide() {
        adapter.save(PaymentTransaction.record(9008L, BigDecimal.TEN, false));
        adapter.save(PaymentTransaction.record(9009L, BigDecimal.TEN, false));

        assertThat(adapter.findByOrderId(9008L)).isPresent();
        assertThat(adapter.findByOrderId(9009L)).isPresent();
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
