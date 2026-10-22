package com.daniellaera.paymentservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IntentVerificationTest {

    private static final BigDecimal TOTAL = new BigDecimal("1399.79");

    @Test
    void succeededForTheExactAmountAndCurrency_pays() {
        assertThat(new IntentVerification("succeeded", 139_979L, "eur").pays(TOTAL, "eur")).isTrue();
    }

    @Test
    void currency_isComparedIgnoringCase() {
        assertThat(new IntentVerification("succeeded", 139_979L, "EUR").pays(TOTAL, "eur")).isTrue();
    }

    @Test
    void wholeAmount_isConvertedToMinorUnits() {
        assertThat(new IntentVerification("succeeded", 1_000L, "eur").pays(new BigDecimal("10"), "eur")).isTrue();
    }

    @Test
    void anyOtherStatus_doesNotPay() {
        assertThat(new IntentVerification("requires_payment_method", 139_979L, "eur").pays(TOTAL, "eur")).isFalse();
        assertThat(new IntentVerification("processing", 139_979L, "eur").pays(TOTAL, "eur")).isFalse();
        assertThat(new IntentVerification(null, 139_979L, "eur").pays(TOTAL, "eur")).isFalse();
    }

    @Test
    void anotherAmount_doesNotPay() {
        assertThat(new IntentVerification("succeeded", 139_978L, "eur").pays(TOTAL, "eur")).isFalse();
        assertThat(new IntentVerification("succeeded", 13_997_900L, "eur").pays(TOTAL, "eur")).isFalse();
    }

    @Test
    void anotherCurrency_doesNotPay() {
        assertThat(new IntentVerification("succeeded", 139_979L, "usd").pays(TOTAL, "eur")).isFalse();
    }

    @Test
    void missingAmountOrCurrency_doesNotPay() {
        assertThat(new IntentVerification("succeeded", null, "eur").pays(TOTAL, "eur")).isFalse();
        assertThat(new IntentVerification("succeeded", 139_979L, null).pays(TOTAL, "eur")).isFalse();
        assertThat(new IntentVerification("succeeded", 139_979L, "eur").pays(null, "eur")).isFalse();
    }

    @Test
    void fractionsOfACent_neverConvert() {
        assertThat(IntentVerification.toMinorUnits(new BigDecimal("1399.795"))).isEmpty();
        assertThat(IntentVerification.toMinorUnits(new BigDecimal("1399.790"))).contains(139_979L);
    }
}
