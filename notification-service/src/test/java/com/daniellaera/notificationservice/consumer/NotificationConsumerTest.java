package com.daniellaera.notificationservice.consumer;

import com.daniellaera.notificationservice.dto.PaymentEvent;
import com.daniellaera.notificationservice.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    private NotificationConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new NotificationConsumer(objectMapper, emailNotificationService);
    }

    private String toJson(PaymentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handlePaymentEvent_SUCCESS_callsSendOrderConfirmed() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2,
                new BigDecimal("1299.99"), new BigDecimal("2599.98"),
                "SUCCESS", "buyer@test.com");

        consumer.handlePaymentEvent(toJson(event));

        verify(emailNotificationService).sendOrderConfirmed(event);
        verifyNoMoreInteractions(emailNotificationService);
    }

    @Test
    void handlePaymentEvent_FAILED_callsSendOrderCancelled() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2,
                new BigDecimal("1299.99"), new BigDecimal("2599.98"),
                "FAILED", "buyer@test.com");

        consumer.handlePaymentEvent(toJson(event));

        verify(emailNotificationService).sendOrderCancelled(event);
        verifyNoMoreInteractions(emailNotificationService);
    }

    @Test
    void handlePaymentEvent_unknownStatus_callsNeither() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2,
                new BigDecimal("1299.99"), new BigDecimal("2599.98"),
                "PENDING", "buyer@test.com");

        consumer.handlePaymentEvent(toJson(event));

        verifyNoInteractions(emailNotificationService);
    }

    @Test
    void handlePaymentEvent_invalidJson_doesNotThrow() {
        assertThatCode(() -> consumer.handlePaymentEvent("not-valid-json"))
                .doesNotThrowAnyException();
        verifyNoInteractions(emailNotificationService);
    }

    @Test
    void handlePaymentEvent_nullUserEmail_stillRoutesCorrectly() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2,
                new BigDecimal("1299.99"), new BigDecimal("2599.98"),
                "SUCCESS", null);

        consumer.handlePaymentEvent(toJson(event));

        // routing is correct — email guard is EmailNotificationService's responsibility
        verify(emailNotificationService).sendOrderConfirmed(event);
    }

    @Test
    void handlePaymentEvent_emptyMessage_doesNotThrow() {
        assertThatCode(() -> consumer.handlePaymentEvent(""))
                .doesNotThrowAnyException();
        verifyNoInteractions(emailNotificationService);
    }
}
