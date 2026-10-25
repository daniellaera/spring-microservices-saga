package com.daniellaera.notificationservice.domain.port.in;

import com.daniellaera.notificationservice.domain.model.PaymentOutcome;

/** Emails the customer that the order was confirmed or cancelled. Never throws. */
public interface NotifyPaymentOutcomeUseCase {

    void notifyOutcome(PaymentOutcome outcome);
}
