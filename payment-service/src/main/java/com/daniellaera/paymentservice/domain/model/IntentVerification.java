package com.daniellaera.paymentservice.domain.model;

import java.math.BigDecimal;
import java.util.Optional;

/** What the payment provider reports about an intent: its status, amount in minor units and currency. */
public record IntentVerification(String status, Long amountMinor, String currency) {

    public boolean succeeded() {
        return "succeeded".equals(status);
    }

    /**
     * True only when the intent succeeded for exactly this amount in this currency. The amount must convert to
     * minor units exactly (scale 2): an amount with fractions of a cent never matches.
     */
    public boolean pays(BigDecimal totalAmount, String expectedCurrency) {
        return succeeded()
                && currency != null && currency.equalsIgnoreCase(expectedCurrency)
                && amountMinor != null && toMinorUnits(totalAmount).filter(amountMinor::equals).isPresent();
    }

    static Optional<Long> toMinorUnits(BigDecimal amount) {
        if (amount == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(amount.setScale(2).movePointRight(2).longValueExact());
        } catch (ArithmeticException e) {
            return Optional.empty();
        }
    }
}
