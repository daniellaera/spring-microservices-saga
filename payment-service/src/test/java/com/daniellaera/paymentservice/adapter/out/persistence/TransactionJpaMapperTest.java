package com.daniellaera.paymentservice.adapter.out.persistence;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionJpaMapperTest {

    @Test
    void toDomain_copiesEveryField() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 22, 10, 0);
        TransactionJpaEntity entity = new TransactionJpaEntity();
        ReflectionTestUtils.setField(entity, "id", 7L);
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        entity.setOrderId(42L);
        entity.setTotalAmount(new BigDecimal("1399.79"));
        entity.setStatus(PaymentStatus.SUCCESS);
        entity.setPaymentIntentId("pi_42");

        assertThat(TransactionJpaMapper.toDomain(entity)).isEqualTo(
                new PaymentTransaction(7L, 42L, new BigDecimal("1399.79"), PaymentStatus.SUCCESS, "pi_42", createdAt));
    }

    @Test
    void toNewEntity_leavesIdAndCreatedAtToJpa() {
        TransactionJpaEntity entity = TransactionJpaMapper.toNewEntity(
                PaymentTransaction.record(42L, new BigDecimal("1399.79"), false));

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getOrderId()).isEqualTo(42L);
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("1399.79");
        assertThat(entity.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void toNewEntity_copiesThePayingIntent() {
        assertThat(TransactionJpaMapper.toNewEntity(PaymentTransaction.paid(42L, BigDecimal.TEN, "pi_42"))
                .getPaymentIntentId()).isEqualTo("pi_42");
    }
}
