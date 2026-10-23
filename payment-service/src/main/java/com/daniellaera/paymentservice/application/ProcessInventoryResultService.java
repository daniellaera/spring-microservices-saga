package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.model.IntentVerification;
import com.daniellaera.paymentservice.domain.model.PaymentIntentAlreadyUsedException;
import com.daniellaera.paymentservice.domain.model.PaymentRefund;
import com.daniellaera.paymentservice.domain.model.PaymentRefundRefusedException;
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
 * saving and publishing), the stored outcome is published again, without asking the gateway, refunding, saving or
 * auditing. Two racing deliveries are settled by the unique constraint: the loser takes the same replay path.
 * <p>
 * An approved order succeeds only when its payment intent succeeded for exactly the order's total, in the shop
 * currency, and only if that intent does not already belong to another order (it then fails without asking the
 * provider; a concurrent order paid with the same intent loses on the unique constraint and fails too). Without an
 * intent (missing or blank), or when the provider refuses the request, it fails. When the provider cannot answer
 * for now, nothing is recorded: PaymentGatewayUnavailableException propagates so that the listener's container
 * retries the inventory result instead of failing the order for good.
 * <p>
 * Payment compensation: the customer pays in Stripe before the order exists, so an order that fails (inventory
 * REJECTED, or an intent for another amount or currency) may have captured money. When its intent succeeded and
 * belongs to no other order, the whole payment is refunded before the failure is recorded, with an idempotency key
 * derived from the order, so a retry or a racing delivery makes one refund. An intent that belongs to another order
 * is never refunded: that money pays for the other order.
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

    /** The transaction to record, and Stripe's error code when it refused to refund the payment. */
    private record Outcome(PaymentTransaction transaction, String refundRefusal) {
        static Outcome of(PaymentTransaction transaction) {
            return new Outcome(transaction, null);
        }
    }

    @Override
    public void process(InventoryResult result) {
        log.info("=== Payment: received inventory result orderId={} status={}", result.orderId(), result.status());

        Optional<PaymentTransaction> recorded = transactionRepository.findByOrderId(result.orderId());
        if (recorded.isPresent()) {
            replay(result, recorded.get());
            return;
        }

        Outcome outcome = decide(result);
        Optional<PaymentTransaction> saved = record(result, outcome.transaction());
        if (saved.isEmpty()) {
            return;
        }
        auditTrail.paymentProcessed(saved.get(), result.userEmail());
        if (saved.get().refunded()) {
            auditTrail.paymentRefunded(saved.get(), result.userEmail());
        }
        if (outcome.refundRefusal() != null) {
            auditTrail.paymentRefundFailed(saved.get(), outcome.refundRefusal(), result.userEmail());
        }
        paymentResultPublisher.publish(result, saved.get().status());
    }

    private Outcome decide(InventoryResult result) {
        if (!result.approved()) {
            log.warn("=== Payment: skipping payment for orderId={} — inventory REJECTED", result.orderId());
        }
        if (!result.hasPaymentIntent()) {
            if (result.approved()) {
                log.warn("=== Payment: no paymentIntentId for orderId {} — FAILED (an order is never free)",
                        result.orderId());
            }
            return Outcome.of(failed(result));
        }
        if (intentBelongsToAnotherOrder(result)) {
            log.warn("=== Payment: intent of orderId {} already belongs to another order — FAILED, not refunded",
                    result.orderId());
            return Outcome.of(failed(result));
        }
        Optional<IntentVerification> verification = verify(result);
        if (result.approved() && verification.map(v -> v.pays(result.totalAmount(), shopCurrency)).orElse(false)) {
            return Outcome.of(PaymentTransaction.paid(result.orderId(), result.totalAmount(), result.paymentIntentId()));
        }
        if (verification.map(IntentVerification::succeeded).orElse(false)) {
            return refund(result);
        }
        return Outcome.of(failed(result));
    }

    // The order failed but its intent captured money: give it back before recording the failure
    private Outcome refund(InventoryResult result) {
        try {
            PaymentRefund refund = paymentGateway.refund(result.paymentIntentId(), "refund-order-" + result.orderId());
            if (PaymentRefund.ALREADY_REFUNDED.equals(refund.refundId())) {
                log.warn("=== Payment: intent of orderId {} was already refunded outside the shop — recorded as refunded",
                        result.orderId());
            } else {
                log.info("=== Payment: refunded intent of orderId {} ({} {})", result.orderId(), refund.refundId(),
                        refund.status());
            }
            return Outcome.of(PaymentTransaction.refunded(result.orderId(), result.totalAmount(),
                    result.paymentIntentId(), refund.refundId()));
        } catch (PaymentRefundRefusedException e) {
            log.error("=== Payment: refund for orderId {} refused, needs a manual refund: {} ({})", result.orderId(),
                    e.getMessage(), e.errorCode());
            return new Outcome(PaymentTransaction.refundRefused(result.orderId(), result.totalAmount(),
                    result.paymentIntentId()), e.errorCode());
        }
    }

    private static PaymentTransaction failed(InventoryResult result) {
        return PaymentTransaction.record(result.orderId(), result.totalAmount(), false);
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
            if (transaction.succeeded()) {
                log.warn("=== Payment: intent of orderId {} paid for another order concurrently — FAILED",
                        result.orderId());
                return record(result, failed(result));
            }
            // another order recorded the intent between our check and this save, after the refund was attempted
            log.error("=== Payment: intent of orderId {} was recorded for another order during its refund (refund: {}) "
                    + "— check it by hand", result.orderId(), transaction.refundId());
            return record(result, transaction.withoutIntent());
        }
    }

    private boolean intentBelongsToAnotherOrder(InventoryResult result) {
        return transactionRepository.findByPaymentIntentId(result.paymentIntentId())
                .filter(other -> !other.orderId().equals(result.orderId()))
                .isPresent();
    }

    private Optional<IntentVerification> verify(InventoryResult result) {
        Optional<IntentVerification> verification = paymentGateway.verifyIntent(result.paymentIntentId());
        boolean paid = result.approved() && verification.map(v -> v.pays(result.totalAmount(), shopCurrency)).orElse(false);
        log.info("=== Payment: Stripe verification orderId={}: {} (intent: {}, order: {} {})", result.orderId(),
                paid ? "SUCCESS" : "FAILED", verification.map(Object::toString).orElse("provider error"),
                result.totalAmount(), shopCurrency);
        return verification;
    }

    private void replay(InventoryResult result, PaymentTransaction recorded) {
        log.info("=== Payment: orderId={} already processed ({}) — publishing the stored outcome again",
                result.orderId(), recorded.status());
        paymentResultPublisher.publish(result, recorded.status());
    }
}
