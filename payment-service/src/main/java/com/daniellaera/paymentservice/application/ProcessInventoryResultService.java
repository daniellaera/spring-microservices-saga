package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.model.IntentVerification;
import com.daniellaera.paymentservice.domain.model.PaymentIntentAlreadyUsedException;
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
 * An approved order succeeds only when its payment intent succeeded for exactly the order's total, in the shop
 * currency, and only if that intent has not already paid for another order (it then fails without asking the
 * provider; a concurrent order paid with the same intent loses on the unique constraint and fails too). Without an
 * intent (missing or blank), or when the provider refuses the request, it fails. When the provider cannot answer
 * for now, nothing is recorded: PaymentGatewayUnavailableException propagates so that the listener's container
 * retries the inventory result instead of failing the order for good.
 */
public class ProcessInventoryResultService implements ProcessInventoryResultUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessInventoryResultService.class);

    private final PaymentGateway paymentGateway;
    private final TransactionRepository transactionRepository;
    private final AuditTrail auditTrail;
    private final PaymentResultPublisher paymentResultPublisher;
    private final String shopCurrency;

    public ProcessInventoryResultService(PaymentGateway paymentGateway, TransactionRepository transactionRepository,
                                         AuditTrail auditTrail, PaymentResultPublisher paymentResultPublisher,
                                         String shopCurrency) {
        this.paymentGateway = paymentGateway;
        this.transactionRepository = transactionRepository;
        this.auditTrail = auditTrail;
        this.paymentResultPublisher = paymentResultPublisher;
        this.shopCurrency = shopCurrency;
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
        if (!result.approved()) {
            log.warn("=== Payment: skipping payment for orderId={} — inventory REJECTED", result.orderId());
        } else if (!result.hasPaymentIntent()) {
            log.warn("=== Payment: no paymentIntentId for orderId {} — FAILED (an order is never free)",
                    result.orderId());
        } else if (intentPaidAnotherOrder(result)) {
            log.warn("=== Payment: intent of orderId {} already paid for another order — FAILED", result.orderId());
        } else {
            paymentSucceeded = verify(result);
        }

        Optional<PaymentTransaction> saved = record(result, paymentSucceeded
                ? PaymentTransaction.paid(result.orderId(), result.totalAmount(), result.paymentIntentId())
                : PaymentTransaction.record(result.orderId(), result.totalAmount(), false));
        if (saved.isEmpty()) {
            return;
        }
        auditTrail.paymentProcessed(saved.get(), result.userEmail());
        paymentResultPublisher.publish(result, saved.get().status());
    }

    /** Saves the transaction; empty when the order turned out to be recorded already and its outcome was replayed. */
    private Optional<PaymentTransaction> record(InventoryResult result, PaymentTransaction transaction) {
        try {
            return Optional.of(transactionRepository.save(transaction));
        } catch (TransactionAlreadyRecordedException e) {
            // a concurrent delivery of the same result recorded it first
            replay(result, transactionRepository.findByOrderId(result.orderId()).orElseThrow(() -> e));
            return Optional.empty();
        } catch (PaymentIntentAlreadyUsedException e) {
            log.warn("=== Payment: intent of orderId {} paid for another order concurrently — FAILED", result.orderId());
            return record(result, PaymentTransaction.record(result.orderId(), result.totalAmount(), false));
        }
    }

    private boolean intentPaidAnotherOrder(InventoryResult result) {
        return transactionRepository.findByPaymentIntentId(result.paymentIntentId())
                .filter(paid -> !paid.orderId().equals(result.orderId()))
                .isPresent();
    }

    private boolean verify(InventoryResult result) {
        Optional<IntentVerification> verification = paymentGateway.verifyIntent(result.paymentIntentId());
        boolean paid = verification.map(v -> v.pays(result.totalAmount(), shopCurrency)).orElse(false);
        log.info("=== Payment: Stripe verification orderId={}: {} (intent: {}, order: {} {})", result.orderId(),
                paid ? "SUCCESS" : "FAILED", verification.map(Object::toString).orElse("provider error"),
                result.totalAmount(), shopCurrency);
        return paid;
    }

    private void replay(InventoryResult result, PaymentTransaction recorded) {
        log.info("=== Payment: orderId={} already processed ({}) — publishing the stored outcome again",
                result.orderId(), recorded.status());
        paymentResultPublisher.publish(result, recorded.status());
    }
}
