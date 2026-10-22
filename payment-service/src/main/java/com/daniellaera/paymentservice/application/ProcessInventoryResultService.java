package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import com.daniellaera.paymentservice.domain.port.in.ProcessInventoryResultUseCase;
import com.daniellaera.paymentservice.domain.port.out.AuditTrail;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;
import com.daniellaera.paymentservice.domain.port.out.PaymentResultPublisher;
import com.daniellaera.paymentservice.domain.port.out.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides the payment outcome of an order inventory has answered for, then records it, audits it and publishes
 * it, in that order. Deliberately not transactional and catching nothing (current behaviour): a failure leaves
 * whatever already happened and propagates to the listener's container. Known bugs kept on purpose (see the
 * backlog): an approved order without an intent is auto-approved, the amount is never checked, and a redelivered
 * result records and publishes a second time.
 */
public class ProcessInventoryResultService implements ProcessInventoryResultUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessInventoryResultService.class);

    private final PaymentGateway paymentGateway;
    private final TransactionRepository transactionRepository;
    private final AuditTrail auditTrail;
    private final PaymentResultPublisher paymentResultPublisher;

    public ProcessInventoryResultService(PaymentGateway paymentGateway, TransactionRepository transactionRepository,
                                         AuditTrail auditTrail, PaymentResultPublisher paymentResultPublisher) {
        this.paymentGateway = paymentGateway;
        this.transactionRepository = transactionRepository;
        this.auditTrail = auditTrail;
        this.paymentResultPublisher = paymentResultPublisher;
    }

    @Override
    public void process(InventoryResult result) {
        log.info("=== Payment: received inventory result orderId={} status={}", result.orderId(), result.status());

        boolean paymentSucceeded = false;

        if (result.approved()) {
            if (result.hasPaymentIntent()) {
                paymentSucceeded = paymentGateway.isSucceeded(result.paymentIntentId());
                log.info("=== Payment: Stripe verification orderId={}: {}",
                        result.orderId(), paymentSucceeded ? "SUCCESS" : "FAILED");
            } else {
                paymentSucceeded = true;
                log.info("=== Payment: no paymentIntentId for orderId {} — auto-approving", result.orderId());
            }
        } else {
            log.warn("=== Payment: skipping payment for orderId={} — inventory REJECTED", result.orderId());
        }

        PaymentTransaction saved = transactionRepository.save(
                PaymentTransaction.record(result.orderId(), result.totalAmount(), paymentSucceeded));
        auditTrail.paymentProcessed(saved, result.userEmail());
        paymentResultPublisher.publish(result, saved.status());
    }
}
