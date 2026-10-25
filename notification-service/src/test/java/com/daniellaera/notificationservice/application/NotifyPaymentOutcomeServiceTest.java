package com.daniellaera.notificationservice.application;

import com.daniellaera.notificationservice.application.fakes.RecordingEmailSender;
import com.daniellaera.notificationservice.domain.model.EmailMessage;
import com.daniellaera.notificationservice.domain.model.PaymentOutcome;
import com.daniellaera.notificationservice.domain.model.PaymentOutcome.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyPaymentOutcomeServiceTest {

    private final RecordingEmailSender sender = new RecordingEmailSender();
    private final NotifyPaymentOutcomeService service = new NotifyPaymentOutcomeService(sender);
    private Locale previousLocale;

    @BeforeEach
    void rootLocale() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previousLocale);
    }

    private static PaymentOutcome outcome(String productName, int quantity, String price, String total, String status,
                                          String email, List<Item> items) {
        return new PaymentOutcome(1L, productName, quantity, price == null ? null : new BigDecimal(price),
                total == null ? null : new BigDecimal(total), status, email, items);
    }

    private EmailMessage onlyEmail() {
        assertThat(sender.sent()).hasSize(1);
        return sender.sent().getFirst();
    }

    @Test
    void confirmed_isSentToTheCustomer() {
        service.notifyOutcome(outcome("MacBook Pro", 2, "1299.99", "2599.98", "SUCCESS", "buyer@test.com", null));

        EmailMessage sent = onlyEmail();
        assertThat(sent.to()).isEqualTo("buyer@test.com");
        assertThat(sent.subject()).contains("confirmed").contains("MacBook Pro");
        assertThat(sent.body()).contains("MacBook Pro").contains("1299.99").contains("2599.98");
    }

    @Test
    void confirmed_withoutAnAddress_orABlankOne_isSkipped() {
        service.notifyOutcome(outcome("MacBook Pro", 2, "1299.99", "2599.98", "SUCCESS", null, null));
        service.notifyOutcome(outcome("MacBook Pro", 2, "1299.99", "2599.98", "SUCCESS", "  ", null));

        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void confirmed_withoutPriceOrTotal_showsZero() {
        service.notifyOutcome(outcome("Widget", 1, null, null, "SUCCESS", "buyer@test.com", null));

        assertThat(onlyEmail().body()).contains("0.00");
    }

    @Test
    void confirmed_multiItem_listsEveryItem_andTheSubjectCountsThem() {
        service.notifyOutcome(outcome("MacBook Pro", 1, "1299.99", "2799.96", "SUCCESS", "buyer@test.com", List.of(
                new Item("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")),
                new Item("AirPods", 2, new BigDecimal("249.99"), new BigDecimal("499.98")),
                new Item("iPhone 16", 1, new BigDecimal("999.99"), new BigDecimal("999.99")))));

        EmailMessage sent = onlyEmail();
        assertThat(sent.subject()).isEqualTo("✅ Order confirmed — 3 items");
        assertThat(sent.body()).contains("MacBook Pro").contains("AirPods").contains("iPhone 16").contains("2799.96");
    }

    @Test
    void confirmed_withEmptyItems_usesTheLegacyProductName() {
        service.notifyOutcome(outcome("MacBook Pro", 2, "1299.99", "2599.98", "SUCCESS", "buyer@test.com", List.of()));

        EmailMessage sent = onlyEmail();
        assertThat(sent.subject()).isEqualTo("✅ Order confirmed — MacBook Pro");
        assertThat(sent.body()).contains("MacBook Pro");
    }

    @Test
    void confirmed_singleItem_subjectIsItsName() {
        service.notifyOutcome(outcome("MacBook Pro", 1, "1299.99", "1299.99", "SUCCESS", "buyer@test.com", null));

        assertThat(onlyEmail().subject()).isEqualTo("✅ Order confirmed — MacBook Pro");
    }

    @Test
    void cancelled_multiItem_listsEveryItem_andTheSubjectCountsThem() {
        service.notifyOutcome(outcome("MacBook Pro", 1, "1299.99", "1799.97", "FAILED", "buyer@test.com", List.of(
                new Item("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")),
                new Item("AirPods", 2, new BigDecimal("249.99"), new BigDecimal("499.98")))));

        EmailMessage sent = onlyEmail();
        assertThat(sent.subject()).isEqualTo("❌ Order cancelled — 2 items");
        assertThat(sent.body()).contains("MacBook Pro").contains("AirPods");
    }

    @Test
    void cancelled_withoutItems_usesTheLegacyProductName() {
        service.notifyOutcome(outcome("iPhone 16", 1, "999.99", "999.99", "FAILED", "buyer@test.com", null));

        EmailMessage sent = onlyEmail();
        assertThat(sent.to()).isEqualTo("buyer@test.com");
        assertThat(sent.subject()).isEqualTo("❌ Order cancelled — iPhone 16");
        assertThat(sent.body()).contains("iPhone 16").contains("999.99");
    }

    @Test
    void cancelled_withoutAnAddress_orABlankOne_isSkipped() {
        service.notifyOutcome(outcome("iPhone 16", 1, "999.99", "999.99", "FAILED", null, null));
        service.notifyOutcome(outcome("iPhone 16", 1, "999.99", "999.99", "FAILED", "", null));

        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void cancelled_withoutTotal_showsZero() {
        service.notifyOutcome(outcome("Widget", 1, "9.99", null, "FAILED", "buyer@test.com", null));

        assertThat(onlyEmail().body()).contains("0.00");
    }

    @Test
    void anyOtherStatus_sendsNothing() {
        service.notifyOutcome(outcome("Widget", 1, "9.99", "9.99", "PENDING", "buyer@test.com", null));
        service.notifyOutcome(outcome("Widget", 1, "9.99", "9.99", null, "buyer@test.com", null));

        assertThat(sender.sent()).isEmpty();
    }
}
