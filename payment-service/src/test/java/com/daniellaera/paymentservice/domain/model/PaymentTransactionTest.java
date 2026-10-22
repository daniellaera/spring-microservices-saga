package com.daniellaera.paymentservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTransactionTest {

    @Test
    void record_succeeded_isASuccessWithoutIdNorCreatedAt() {
        PaymentTransaction transaction = PaymentTransaction.record(42L, new BigDecimal("1399.79"), true);

        assertThat(transaction.id()).isNull();
        assertThat(transaction.createdAt()).isNull();
        assertThat(transaction.orderId()).isEqualTo(42L);
        assertThat(transaction.totalAmount()).isEqualByComparingTo("1399.79");
        assertThat(transaction.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.succeeded()).isTrue();
    }

    @Test
    void record_notSucceeded_isAFailure() {
        PaymentTransaction transaction = PaymentTransaction.record(7L, new BigDecimal("179.00"), false);

        assertThat(transaction.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(transaction.succeeded()).isFalse();
    }

    @Test
    void record_keepsAMissingAmount() {
        assertThat(PaymentTransaction.record(7L, null, true).totalAmount()).isNull();
    }

    @Test
    void record_hasNoPayingIntent() {
        assertThat(PaymentTransaction.record(7L, BigDecimal.TEN, false).paymentIntentId()).isNull();
    }

    @Test
    void paid_isASuccessByThatIntent() {
        PaymentTransaction transaction = PaymentTransaction.paid(42L, new BigDecimal("1399.79"), "pi_42");

        assertThat(transaction.id()).isNull();
        assertThat(transaction.createdAt()).isNull();
        assertThat(transaction.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.paymentIntentId()).isEqualTo("pi_42");
    }
}
