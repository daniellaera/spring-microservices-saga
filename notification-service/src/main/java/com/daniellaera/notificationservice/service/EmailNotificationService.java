package com.daniellaera.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import com.daniellaera.notificationservice.dto.OrderItemEvent;
import com.daniellaera.notificationservice.dto.OtpMessage;
import com.daniellaera.notificationservice.dto.PaymentEvent;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendOrderConfirmed(PaymentEvent event) {
        if (event.userEmail() == null || event.userEmail().isBlank()) {
            log.warn("=== No email for order {} — skipping", event.orderId());
            return;
        }

        String subject = "✅ Order confirmed — " + itemsSummary(event);
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
                itemsTable(event),
                event.totalAmount() != null ? event.totalAmount().doubleValue() : 0.0
        );

        send(event.userEmail(), subject, body);
    }

    public void sendOrderCancelled(PaymentEvent event) {
        if (event.userEmail() == null || event.userEmail().isBlank()) {
            log.warn("=== No email for order {} — skipping", event.orderId());
            return;
        }

        String subject = "❌ Order cancelled — " + itemsSummary(event);
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
                itemsTable(event),
                event.totalAmount() != null ? event.totalAmount().doubleValue() : 0.0
        );

        send(event.userEmail(), subject, body);
    }

    public void sendOtpCode(OtpMessage message) {
        if (message.email() == null || message.email().isBlank()) {
            log.warn("=== OTP: no email address — skipping");
            return;
        }

        String subject = "Your verification code — Online Shop";
        String body = """
                Hi,

                Your verification code is:

                    %s

                This code expires in 5 minutes.
                If you didn't request this, ignore this email.

                — Online Shop Security
                """.formatted(message.otp());

        send(message.email(), subject, body);
    }

    private List<OrderItemEvent> getItems(PaymentEvent event) {
        if (event.items() != null && !event.items().isEmpty()) {
            return event.items();
        }
        return List.of(new OrderItemEvent(event.productName(), event.quantity(), event.price(), event.totalAmount()));
    }

    private String itemsSummary(PaymentEvent event) {
        List<OrderItemEvent> items = getItems(event);
        if (items.size() == 1) {
            return items.get(0).productName();
        }
        return items.size() + " items";
    }

    private String itemsTable(PaymentEvent event) {
        StringBuilder table = new StringBuilder();
        for (OrderItemEvent item : getItems(event)) {
            BigDecimal unitPrice = item.price() != null ? item.price() : BigDecimal.ZERO;
            BigDecimal lineTotal = item.totalAmount() != null ? item.totalAmount() : BigDecimal.ZERO;
            table.append(String.format("  • %-30s x%d  @ %.2f €  = %.2f €%n",
                    item.productName(), item.quantity(), unitPrice.doubleValue(), lineTotal.doubleValue()));
        }
        return table.toString();
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("=== Email sent to {} subject: {}", to, subject);
        } catch (Exception e) {
            log.error("=== Failed to send email to {}: {}", to, e.getMessage());
            // never throw — email failure must NOT break the saga
        }
    }
}
