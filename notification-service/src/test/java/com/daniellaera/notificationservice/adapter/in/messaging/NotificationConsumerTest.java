package com.daniellaera.notificationservice.adapter.in.messaging;

import com.daniellaera.notificationservice.domain.model.PaymentOutcome;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationConsumerTest {

    private final List<PaymentOutcome> notified = new ArrayList<>();
    private final NotificationConsumer consumer = new NotificationConsumer(JsonMapper.builder().build(), notified::add);

    @Test
    void aPaymentEvent_isPassedOnWithEveryFieldAndItem() {
        consumer.handlePaymentEvent("""
                {"orderId":1,"productName":"MacBook Pro","quantity":1,"price":1299.99,"totalAmount":1349.89,
                 "status":"SUCCESS","userEmail":"buyer@test.com",
                 "items":[{"productName":"USB-C Hub","quantity":1,"price":49.90,"totalAmount":49.90}]}""");

        assertThat(notified).containsExactly(new PaymentOutcome(1L, "MacBook Pro", 1, new BigDecimal("1299.99"),
                new BigDecimal("1349.89"), "SUCCESS", "buyer@test.com",
                List.of(new PaymentOutcome.Item("USB-C Hub", 1, new BigDecimal("49.90"), new BigDecimal("49.90")))));
    }

    @Test
    void failedAndUnknownStatuses_andAMissingEmail_arePassedOnToo() {
        consumer.handlePaymentEvent("{\"orderId\":2,\"status\":\"FAILED\",\"userEmail\":\"buyer@test.com\"}");
        consumer.handlePaymentEvent("{\"orderId\":3,\"status\":\"UNKNOWN\",\"userEmail\":\"buyer@test.com\"}");
        consumer.handlePaymentEvent("{\"orderId\":4,\"status\":\"SUCCESS\",\"userEmail\":null}");

        assertThat(notified).extracting(PaymentOutcome::status).containsExactly("FAILED", "UNKNOWN", "SUCCESS");
        assertThat(notified.get(2).userEmail()).isNull();
        assertThat(notified.get(0).items()).isNull();
    }

    @Test
    void anUnreadableOrEmptyMessage_isSwallowed() {
        assertThatCode(() -> consumer.handlePaymentEvent("not-json")).doesNotThrowAnyException();
        assertThatCode(() -> consumer.handlePaymentEvent("")).doesNotThrowAnyException();

        assertThat(notified).isEmpty();
    }
}
