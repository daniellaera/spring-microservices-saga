package com.daniellaera.notificationservice.application;

import com.daniellaera.notificationservice.domain.model.EmailMessage;
import com.daniellaera.notificationservice.domain.model.PaymentOutcome;

import java.math.BigDecimal;
import java.util.List;

/** The order emails' wording. Amounts are formatted with the JVM's default locale. */
final class OrderEmails {

    private OrderEmails() {
    }

    static EmailMessage confirmed(PaymentOutcome outcome) {
        String subject = "✅ Order confirmed — " + itemsSummary(outcome);
        String body = """
                Hi,

                Great news! Your order has been confirmed.

                Order details:
                ─────────────────────────────────────
                %s
                ─────────────────────────────────────
                Total:     %.2f €

                Thank you for your purchase!

                — Online Shop
                """.formatted(
                itemsTable(outcome),
                outcome.totalAmount() != null ? outcome.totalAmount().doubleValue() : 0.0
        );
        return new EmailMessage(outcome.userEmail(), subject, body);
    }

    static EmailMessage cancelled(PaymentOutcome outcome) {
        String subject = "❌ Order cancelled — " + itemsSummary(outcome);
        String body = """
                Hi,

                Unfortunately your order could not be processed.

                Order details:
                ─────────────────────────────────────
                %s
                ─────────────────────────────────────
                Total:     %.2f €

                Your inventory has been automatically restored.
                Please try again or contact support.

                — Online Shop
                """.formatted(
                itemsTable(outcome),
                outcome.totalAmount() != null ? outcome.totalAmount().doubleValue() : 0.0
        );
        return new EmailMessage(outcome.userEmail(), subject, body);
    }

    private static String itemsSummary(PaymentOutcome outcome) {
        List<PaymentOutcome.Item> items = outcome.lines();
        if (items.size() == 1) {
            return items.get(0).productName();
        }
        return items.size() + " items";
    }

    private static String itemsTable(PaymentOutcome outcome) {
        StringBuilder table = new StringBuilder();
        for (PaymentOutcome.Item item : outcome.lines()) {
            BigDecimal unitPrice = item.price() != null ? item.price() : BigDecimal.ZERO;
            BigDecimal lineTotal = item.totalAmount() != null ? item.totalAmount() : BigDecimal.ZERO;
            table.append(String.format("  • %-30s x%d  @ %.2f €  = %.2f €%n",
                    item.productName(), item.quantity(), unitPrice.doubleValue(), lineTotal.doubleValue()));
        }
        return table.toString();
    }
}
