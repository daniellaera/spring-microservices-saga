package com.daniellaera.notificationservice.application;

import com.daniellaera.notificationservice.domain.model.PaymentOutcome;
import com.daniellaera.notificationservice.domain.port.in.NotifyPaymentOutcomeUseCase;
import com.daniellaera.notificationservice.domain.port.out.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotifyPaymentOutcomeService implements NotifyPaymentOutcomeUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotifyPaymentOutcomeService.class);

    private final EmailSender emailSender;

    public NotifyPaymentOutcomeService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    // SUCCESS: confirmed, FAILED: cancelled, anything else only logged; nobody to write to: skipped
    @Override
    public void notifyOutcome(PaymentOutcome outcome) {
        if (outcome.succeeded()) {
            sendIfAddressed(outcome, true);
        } else if (outcome.failed()) {
            sendIfAddressed(outcome, false);
        } else {
            log.warn("=== Unknown status: {}", outcome.status());
        }
    }

    private void sendIfAddressed(PaymentOutcome outcome, boolean confirmed) {
        if (!outcome.hasRecipient()) {
            log.warn("=== No email for order {} — skipping", outcome.orderId());
            return;
        }
        emailSender.send(confirmed ? OrderEmails.confirmed(outcome) : OrderEmails.cancelled(outcome));
    }
}
