package com.daniellaera.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import com.daniellaera.notificationservice.dto.PaymentEvent;

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

        String subject = "✅ Order confirmed — " + event.productName();
        String body = """
                Hi,

                Great news! Your order has been confirmed.

                Order details:
                ─────────────────────────
                Product:   %s
                Quantity:  %d
                Price:     %.2f €
                Total:     %.2f €
                ─────────────────────────

                Thank you for your purchase!

                — Online Shop
                """.formatted(
                event.productName(),
                event.quantity(),
                event.price() != null ? event.price().doubleValue() : 0.0,
                event.totalAmount() != null ? event.totalAmount().doubleValue() : 0.0
        );

        send(event.userEmail(), subject, body);
    }

    public void sendOrderCancelled(PaymentEvent event) {
        if (event.userEmail() == null || event.userEmail().isBlank()) {
            log.warn("=== No email for order {} — skipping", event.orderId());
            return;
        }

        String subject = "❌ Order cancelled — " + event.productName();
        String body = """
                Hi,

                Unfortunately your order could not be processed.

                Order details:
                ─────────────────────────
                Product:   %s
                Quantity:  %d
                Total:     %.2f €
                ─────────────────────────

                Your inventory has been automatically restored.
                Please try again or contact support.

                — Online Shop
                """.formatted(
                event.productName(),
                event.quantity(),
                event.totalAmount() != null ? event.totalAmount().doubleValue() : 0.0
        );

        send(event.userEmail(), subject, body);
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
