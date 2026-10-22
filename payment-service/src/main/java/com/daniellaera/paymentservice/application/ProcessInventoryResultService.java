package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.model.TransactionAlreadyRecordedException;
import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import com.daniellaera.paymentservice.domain.port.in.ProcessInventoryResultUseCase;
import com.daniellaera.paymentservice.domain.port.out.AuditTrail;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;
import com.daniellaera.paymentservice.domain.port.out.PaymentResultPublisher;
import com.daniellaera.paymentservice.domain.port.out.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Decides the payment outcome of an order inventory has answered for, then records it, audits it and publishes
 * it, in that order. Deliberately not transactional: a failure leaves whatever already happened and propagates to
 * the listener's container.
 * <p>
 * Idempotent per order: when a transaction is already recorded (a redelivery, or a retry after a crash between
 * saving and publishing), the stored outcome is published again, without asking the gateway, saving or auditing.
 * Two racing deliveries are settled by the unique constraint: the loser takes the same replay path.
 * <p>
 * Known bugs kept on purpose (see the backlog): an approved order without an intent is auto-approved and the
 * amount is never checked.
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

        Optional<PaymentTransaction> recorded = transactionRepository.findByOrderId(result.orderId());
        if (recorded.isPresent()) {
            replay(result, recorded.get());
            return;
        }

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

        PaymentTransaction saved;
        try {
            saved = transactionRepository.save(
                    PaymentTransaction.record(result.orderId(), result.totalAmount(), paymentSucceeded));
        } catch (TransactionAlreadyRecordedException e) {
            // a concurrent delivery recorded it first
            replay(result, transactionRepository.findByOrderId(result.orderId()).orElseThrow(() -> e));
            return;
        }
        auditTrail.paymentProcessed(saved, result.userEmail());
        paymentResultPublisher.publish(result, saved.status());
    }

    private void replay(InventoryResult result, PaymentTransaction recorded) {
        log.info("=== Payment: orderId={} already processed ({}) — publishing the stored outcome again",
                result.orderId(), recorded.status());
        paymentResultPublisher.publish(result, recorded.status());
    }
}
