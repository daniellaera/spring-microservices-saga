package com.daniellaera.notificationservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOutcomeTest {

    private static PaymentOutcome outcome(String status, String email, List<PaymentOutcome.Item> items) {
        return new PaymentOutcome(7L, "Lamp", 2, new BigDecimal("49.90"), new BigDecimal("99.80"), status, email, items);
    }

    @Test
    void theItems_whenThereAreAny() {
        PaymentOutcome.Item hub = new PaymentOutcome.Item("USB-C Hub", 1, new BigDecimal("49.90"), new BigDecimal("49.90"));

        assertThat(outcome("SUCCESS", "a@b.c", List.of(hub)).lines()).containsExactly(hub);
    }

    @Test
    void oneLineFromTheLegacyFields_whenItemsAreMissingOrEmpty() {
        PaymentOutcome.Item legacy = new PaymentOutcome.Item("Lamp", 2, new BigDecimal("49.90"), new BigDecimal("99.80"));

        assertThat(outcome("SUCCESS", "a@b.c", null).lines()).containsExactly(legacy);
        assertThat(outcome("SUCCESS", "a@b.c", List.of()).lines()).containsExactly(legacy);
    }

    @Test
    void statusAndRecipient() {
        assertThat(outcome("SUCCESS", "a@b.c", null).succeeded()).isTrue();
        assertThat(outcome("FAILED", "a@b.c", null).failed()).isTrue();
        assertThat(outcome("PENDING", "a@b.c", null).succeeded()).isFalse();
        assertThat(outcome("PENDING", "a@b.c", null).failed()).isFalse();
        assertThat(outcome("SUCCESS", null, null).hasRecipient()).isFalse();
        assertThat(outcome("SUCCESS", "  ", null).hasRecipient()).isFalse();
        assertThat(new OtpNotification(null, "1").hasRecipient()).isFalse();
        assertThat(new OtpNotification("a@b.c", "1").hasRecipient()).isTrue();
    }
}
