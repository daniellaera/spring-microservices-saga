package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentUseCase;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;
import com.daniellaera.paymentservice.domain.port.out.AuditTrail;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreatePaymentIntentService implements CreatePaymentIntentUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentIntentService.class);

    private final PaymentGateway paymentGateway;
    private final AuditTrail auditTrail;

    public CreatePaymentIntentService(PaymentGateway paymentGateway, AuditTrail auditTrail) {
        this.paymentGateway = paymentGateway;
        this.auditTrail = auditTrail;
    }

    @Override
    public CreatedPaymentIntent createIntent(CreatePaymentIntentCommand command) {
        CreatedPaymentIntent intent = paymentGateway.createIntent(command.amount(), command.currency(),
                "Purchase: " + command.productName());
        log.info("=== Stripe: created PaymentIntent {} for product '{}'", intent.id(), command.productName());

        auditTrail.paymentInitiated(intent.id(), command);
        return intent;
    }
}
