package com.daniellaera.notificationservice.service;

import com.daniellaera.notificationservice.dto.OrderItemEvent;
import com.daniellaera.notificationservice.dto.OtpMessage;
import com.daniellaera.notificationservice.dto.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailNotificationService, "fromEmail", "shop@test.com");
    }

    @Test
    void sendOrderConfirmed_sendsEmailWithCorrectFields() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2,
                new BigDecimal("1299.99"), new BigDecimal("2599.98"),
                "SUCCESS", "buyer@test.com", null);

        emailNotificationService.sendOrderConfirmed(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("buyer@test.com");
        assertThat(sent.getFrom()).isEqualTo("shop@test.com");
        assertThat(sent.getSubject()).contains("confirmed").contains("MacBook Pro");
        assertThat(sent.getText()).contains("MacBook Pro").contains("1299.99").contains("2599.98");
    }

    @Test
    void sendOrderConfirmed_skipsEmail_whenUserEmailIsNull() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2,
                new BigDecimal("1299.99"), new BigDecimal("2599.98"),
                "SUCCESS", null, null);

        emailNotificationService.sendOrderConfirmed(event);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendOrderConfirmed_skipsEmail_whenUserEmailIsBlank() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2,
                new BigDecimal("1299.99"), new BigDecimal("2599.98"),
                "SUCCESS", "  ", null);

        emailNotificationService.sendOrderConfirmed(event);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendOrderConfirmed_handlesNullPriceAndTotal_withZero() {
        PaymentEvent event = new PaymentEvent(1L, "Widget", 1,
                null, null, "SUCCESS", "buyer@test.com", null);

        emailNotificationService.sendOrderConfirmed(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("0.00");
    }

    @Test
    void sendOrderConfirmed_swallowsSmtpException() {
        PaymentEvent event = new PaymentEvent(1L, "iPad", 1,
                new BigDecimal("599.99"), new BigDecimal("599.99"),
                "SUCCESS", "buyer@test.com", null);
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailNotificationService.sendOrderConfirmed(event))
                .doesNotThrowAnyException();
    }

    @Test
    void sendOrderConfirmed_multiItem_bodyContainsAllItemsAndSubjectSaysItemCount() {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")),
                new OrderItemEvent("AirPods", 2, new BigDecimal("249.99"), new BigDecimal("499.98")),
                new OrderItemEvent("iPhone 16", 1, new BigDecimal("999.99"), new BigDecimal("999.99"))
        );
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 1,
                new BigDecimal("1299.99"), new BigDecimal("2799.96"),
                "SUCCESS", "buyer@test.com", items);

        emailNotificationService.sendOrderConfirmed(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("✅ Order confirmed — 3 items");
        assertThat(sent.getText())
                .contains("MacBook Pro").contains("AirPods").contains("iPhone 16")
                .contains("2799.96");
    }

    @Test
    void sendOrderConfirmed_emptyItemsList_fallsBackToLegacyProductName() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2,
                new BigDecimal("1299.99"), new BigDecimal("2599.98"),
                "SUCCESS", "buyer@test.com", List.of());

        emailNotificationService.sendOrderConfirmed(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("✅ Order confirmed — MacBook Pro");
        assertThat(sent.getText()).contains("MacBook Pro");
    }

    @Test
    void sendOrderConfirmed_singleItem_subjectUsesProductName() {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 1,
                new BigDecimal("1299.99"), new BigDecimal("1299.99"),
                "SUCCESS", "buyer@test.com", null);

        emailNotificationService.sendOrderConfirmed(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("✅ Order confirmed — MacBook Pro");
    }

    @Test
    void sendOrderCancelled_multiItem_bodyContainsAllItemsAndSubjectSaysItemCount() {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")),
                new OrderItemEvent("AirPods", 2, new BigDecimal("249.99"), new BigDecimal("499.98"))
        );
        PaymentEvent event = new PaymentEvent(2L, "MacBook Pro", 1,
                new BigDecimal("1299.99"), new BigDecimal("1799.97"),
                "FAILED", "buyer@test.com", items);

        emailNotificationService.sendOrderCancelled(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("❌ Order cancelled — 2 items");
        assertThat(sent.getText()).contains("MacBook Pro").contains("AirPods");
    }

    @Test
    void sendOrderCancelled_nullItemsList_fallsBackToLegacyProductName() {
        PaymentEvent event = new PaymentEvent(2L, "iPhone 16", 1,
                new BigDecimal("999.99"), new BigDecimal("999.99"),
                "FAILED", "buyer@test.com", null);

        emailNotificationService.sendOrderCancelled(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("❌ Order cancelled — iPhone 16");
        assertThat(sent.getText()).contains("iPhone 16");
    }

    @Test
    void sendOrderCancelled_sendsEmailWithCorrectFields() {
        PaymentEvent event = new PaymentEvent(2L, "iPhone 16", 1,
                new BigDecimal("999.99"), new BigDecimal("999.99"),
                "FAILED", "buyer@test.com", null);

        emailNotificationService.sendOrderCancelled(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("buyer@test.com");
        assertThat(sent.getFrom()).isEqualTo("shop@test.com");
        assertThat(sent.getSubject()).contains("cancelled").contains("iPhone 16");
        assertThat(sent.getText()).contains("iPhone 16").contains("999.99");
    }

    @Test
    void sendOrderCancelled_skipsEmail_whenUserEmailIsNull() {
        PaymentEvent event = new PaymentEvent(2L, "iPhone 16", 1,
                new BigDecimal("999.99"), new BigDecimal("999.99"),
                "FAILED", null, null);

        emailNotificationService.sendOrderCancelled(event);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendOrderCancelled_skipsEmail_whenUserEmailIsBlank() {
        PaymentEvent event = new PaymentEvent(2L, "iPhone 16", 1,
                new BigDecimal("999.99"), new BigDecimal("999.99"),
                "FAILED", "", null);

        emailNotificationService.sendOrderCancelled(event);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendOrderCancelled_handlesNullTotal_withZero() {
        PaymentEvent event = new PaymentEvent(2L, "Widget", 1,
                new BigDecimal("9.99"), null, "FAILED", "buyer@test.com", null);

        emailNotificationService.sendOrderCancelled(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("0.00");
    }

    @Test
    void sendOrderCancelled_swallowsSmtpException() {
        PaymentEvent event = new PaymentEvent(2L, "iPad", 1,
                new BigDecimal("599.99"), new BigDecimal("599.99"),
                "FAILED", "buyer@test.com", null);
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailNotificationService.sendOrderCancelled(event))
                .doesNotThrowAnyException();
    }

    @Test
    void sendOtpCode_sendsEmailWithCode() {
        OtpMessage message = new OtpMessage("buyer@test.com", "123456", "2026-07-16T10:00:00");

        emailNotificationService.sendOtpCode(message);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("buyer@test.com");
        assertThat(sent.getFrom()).isEqualTo("shop@test.com");
        assertThat(sent.getSubject()).contains("verification code");
        assertThat(sent.getText()).contains("123456");
    }

    @Test
    void sendOtpCode_skipsEmail_whenEmailIsNull() {
        OtpMessage message = new OtpMessage(null, "123456", "2026-07-16T10:00:00");

        emailNotificationService.sendOtpCode(message);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendOtpCode_skipsEmail_whenEmailIsBlank() {
        OtpMessage message = new OtpMessage("  ", "123456", "2026-07-16T10:00:00");

        emailNotificationService.sendOtpCode(message);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendOtpCode_swallowsSmtpException() {
        OtpMessage message = new OtpMessage("buyer@test.com", "123456", "2026-07-16T10:00:00");
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailNotificationService.sendOtpCode(message))
                .doesNotThrowAnyException();
    }
}
