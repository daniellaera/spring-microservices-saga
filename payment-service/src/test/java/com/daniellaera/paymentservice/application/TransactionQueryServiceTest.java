package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.application.fakes.EffectLog;
import com.daniellaera.paymentservice.application.fakes.InMemoryTransactionRepository;
import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.model.TransactionNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ports the read scenarios of the former PaymentServiceImplTest. */
class TransactionQueryServiceTest {

    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository(new EffectLog());
    private final TransactionQueryService service = new TransactionQueryService(transactions);

    @Test
    void getAllTransactions_returnsEveryTransaction() {
        transactions.save(PaymentTransaction.record(1L, new BigDecimal("999.99"), true));

        assertThat(service.getAllTransactions()).singleElement().satisfies(t -> {
            assertThat(t.orderId()).isEqualTo(1L);
            assertThat(t.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(t.totalAmount()).isEqualByComparingTo("999.99");
        });
    }

    @Test
    void getTransactionById_returnsTheTransaction() {
        PaymentTransaction saved = transactions.save(PaymentTransaction.record(1L, new BigDecimal("999.99"), true));

        assertThat(service.getTransactionById(saved.id())).isEqualTo(saved);
    }

    @Test
    void getTransactionById_unknownId_throwsWithTheContractMessage() {
        assertThatThrownBy(() -> service.getTransactionById(999L))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("Transaction not found with id: 999");
    }
}
