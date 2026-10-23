package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.application.fakes.EffectLog;
import com.daniellaera.paymentservice.application.fakes.FakePaymentGateway;
import com.daniellaera.paymentservice.application.fakes.InMemoryTransactionRepository;
import com.daniellaera.paymentservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.paymentservice.application.fakes.RecordingPaymentResultPublisher;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayUnavailableException;
import com.daniellaera.paymentservice.domain.model.PaymentRefund;
import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ports the handleInventoryResult scenarios of the former PaymentServiceImplTest. */
class ProcessInventoryResultServiceTest {

    private final EffectLog effectLog = new EffectLog();
    private final FakePaymentGateway gateway = new FakePaymentGateway(effectLog);
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository(effectLog);
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail(effectLog);
    private final RecordingPaymentResultPublisher publisher = new RecordingPaymentResultPublisher(effectLog);
    private final ProcessInventoryResultService service =
            new ProcessInventoryResultService(gateway, transactions, auditTrail, publisher, "eur");

    @Test
    void approvedAndIntentSucceeded_recordsAuditsAndPublishesSuccess() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        InventoryResult result = approved(1L, "pi_123", null);

        service.process(result);

        assertThat(gateway.verifiedIntents()).containsExactly("pi_123");
        assertThat(transactions.findAll()).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(t.orderId()).isEqualTo(1L);
            assertThat(t.totalAmount()).isEqualByComparingTo("1299.99");
        });
        assertThat(auditTrail.processed()).singleElement().satisfies(audit -> {
            assertThat(audit.transaction()).isEqualTo(transactions.findAll().get(0));
            assertThat(audit.userEmail()).isEqualTo("user@test.com");
        });
        assertThat(publisher.published()).containsExactly(
                new RecordingPaymentResultPublisher.Published(result, PaymentStatus.SUCCESS));
    }

    @Test
    void approved_itemsArePassedThroughUntouched() {
        gateway.givenIntent("pi_999", "succeeded", 129_999L, "eur");
        List<InventoryResult.Item> items = List.of(
                new InventoryResult.Item("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")));

        service.process(approved(5L, "pi_999", items));

        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.result().items()).isEqualTo(items));
    }

    @Test
    void approvedAndIntentNotSucceeded_recordsAndPublishesFailure() {
        gateway.givenIntent("pi_456", "requires_payment_method", 129_999L, "eur");

        service.process(approved(2L, "pi_456", null));

        assertThat(transactions.findAll()).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(auditTrail.processed()).singleElement()
                .satisfies(audit -> assertThat(audit.transaction().status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void approvedAndGatewayError_isAFailure() {
        // the fake answers nothing for an intent it does not know, as the Stripe adapter does on a StripeException
        service.process(approved(2L, "pi_unknown", null));

        assertThat(gateway.verifiedIntents()).containsExactly("pi_unknown");
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void providerUnavailable_propagatesAndRecordsNothing_soTheRetryCanStillPay() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        gateway.unavailableFor(1);
        InventoryResult result = approved(1L, "pi_123", null);

        assertThatThrownBy(() -> service.process(result)).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(effectLog.effects()).isEmpty();

        // the container redelivers the inventory result once Stripe answers again
        service.process(result);

        assertThat(transactions.findAll()).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(PaymentStatus.SUCCESS));
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.SUCCESS));
    }

    @Test
    void approvedAndIntentSucceededForAnotherAmount_isAFailure_andItsPaymentIsRefunded() {
        // was approvedAndIntentSucceededForAnotherAmount_isAFailure: the captured 1.00 was kept
        gateway.givenIntent("pi_low", "succeeded", 100L, "eur");

        service.process(approved(2L, "pi_low", null));

        assertThat(gateway.verifiedIntents()).containsExactly("pi_low");
        assertThat(gateway.refundRequests()).containsExactly(new FakePaymentGateway.RefundRequest("pi_low", "refund-order-2"));
        assertThat(transactions.findAll()).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.refundId()).isEqualTo("re_1_pi_low");
        });
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void approvedAndIntentSucceededInAnotherCurrency_isAFailure_andItsPaymentIsRefunded() {
        gateway.givenIntent("pi_usd", "succeeded", 129_999L, "usd");

        service.process(approved(2L, "pi_usd", null));

        assertThat(gateway.refunds()).hasSize(1);
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void approvedAndIntentNotSucceeded_hasNothingToRefund() {
        gateway.givenIntent("pi_456", "requires_payment_method", 129_999L, "eur");

        service.process(approved(2L, "pi_456", null));

        assertThat(gateway.refundRequests()).isEmpty();
        assertThat(transactions.findByOrderId(2L)).hasValueSatisfying(t -> assertThat(t.refundId()).isNull());
    }

    @Test
    void shopCurrency_comesFromConfiguration() {
        ProcessInventoryResultService chfShop =
                new ProcessInventoryResultService(gateway, transactions, auditTrail, publisher, "chf");
        gateway.givenIntent("pi_chf", "succeeded", 129_999L, "chf");

        chfShop.process(approved(2L, "pi_chf", null));

        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.SUCCESS));
    }

    @Test
    void orderTotalWithFractionsOfACent_neverMatches() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");

        service.process(new InventoryResult(2L, "APPROVED", "MacBook Pro", 1, new BigDecimal("1299.99"),
                new BigDecimal("1299.995"), "user@test.com", "pi_123", null));

        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void success_recordsThePayingIntent_failureRecordsNone() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        gateway.givenIntent("pi_456", "requires_payment_method", 129_999L, "eur");

        service.process(approved(1L, "pi_123", null));
        service.process(approved(2L, "pi_456", null));

        assertThat(transactions.findAll()).extracting(PaymentTransaction::paymentIntentId)
                .containsExactly("pi_123", null);
    }

    @Test
    void intentThatAlreadyPaidAnotherOrder_isAFailureWithoutAskingTheGateway_andIsNeverRefunded() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        service.process(approved(1L, "pi_123", null));

        service.process(approved(2L, "pi_123", null));

        assertThat(gateway.verifiedIntents()).containsExactly("pi_123");
        // that money pays for order 1
        assertThat(gateway.refundRequests()).isEmpty();
        assertThat(auditTrail.refunded()).isEmpty();
        assertThat(transactions.findByOrderId(2L)).hasValueSatisfying(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.paymentIntentId()).isNull();
        });
        assertThat(auditTrail.processed()).extracting(audit -> audit.transaction().status())
                .containsExactly(PaymentStatus.SUCCESS, PaymentStatus.FAILED);
        assertThat(publisher.published()).extracting(RecordingPaymentResultPublisher.Published::status)
                .containsExactly(PaymentStatus.SUCCESS, PaymentStatus.FAILED);
    }

    @Test
    void rejectedOrderWithAnIntentThatPaidAnotherOrder_isNeverRefunded() {
        // the "intent reused" case: the money belongs to the order it paid for
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        service.process(approved(1L, "pi_123", null));

        service.process(rejected(2L, "pi_123"));

        assertThat(gateway.verifiedIntents()).containsExactly("pi_123");
        assertThat(gateway.refundRequests()).isEmpty();
        assertThat(auditTrail.refunded()).isEmpty();
        assertThat(transactions.findByOrderId(1L)).hasValueSatisfying(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(t.paymentIntentId()).isEqualTo("pi_123");
        });
        assertThat(transactions.findByOrderId(2L)).hasValueSatisfying(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.paymentIntentId()).isNull();
            assertThat(t.refundId()).isNull();
        });
    }

    @Test
    void intentRefundedForARejectedOrder_cannotPayAnotherOrder() {
        // was intentThatFailedForAnotherOrder_canStillPayThisOne: the rejected order kept the money, so it could pay
        // again. Refunded, it stays with the rejected order: a refunded intent still reads "succeeded" at Stripe
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        service.process(rejected(1L, "pi_123"));

        service.process(approved(2L, "pi_123", null));

        assertThat(transactions.findByOrderId(2L)).hasValueSatisfying(
                t -> assertThat(t.status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(gateway.refunds()).hasSize(1);
    }

    @Test
    void intentDeclinedForAnotherOrder_canStillPayThisOne() {
        // a declined attempt captured nothing: the intent is not recorded and can still pay once it succeeds
        gateway.givenIntent("pi_123", "requires_payment_method", 129_999L, "eur");
        service.process(approved(1L, "pi_123", null));
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");

        service.process(approved(2L, "pi_123", null));

        assertThat(transactions.findByOrderId(2L)).hasValueSatisfying(
                t -> assertThat(t.status()).isEqualTo(PaymentStatus.SUCCESS));
    }

    @Test
    void concurrentOrderPaidWithTheSameIntent_losesTheRaceAndFails() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        transactions.insertConcurrentlyBeforeNextSave(PaymentTransaction.paid(9L, new BigDecimal("1299.99"), "pi_123"));

        service.process(approved(2L, "pi_123", null));

        assertThat(transactions.findByOrderId(2L)).hasValueSatisfying(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.paymentIntentId()).isNull();
        });
        // order 9 got the money: never refunded
        assertThat(gateway.refundRequests()).isEmpty();
        assertThat(auditTrail.processed()).singleElement()
                .satisfies(audit -> assertThat(audit.transaction().status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void approvedWithoutIntent_isAFailureWithoutAskingTheGateway() {
        // was approvedWithoutIntent_isAutoApprovedWithoutAskingTheGateway: an order without an intent was free
        service.process(approved(3L, null, null));

        assertThat(gateway.verifiedIntents()).isEmpty();
        assertThat(transactions.findAll()).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(auditTrail.processed()).singleElement()
                .satisfies(audit -> assertThat(audit.transaction().status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void approvedWithBlankIntent_isAFailureToo() {
        // was approvedWithBlankIntent_isAutoApprovedToo
        service.process(approved(3L, "  ", null));

        assertThat(gateway.verifiedIntents()).isEmpty();
        assertThat(transactions.findAll()).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void rejectedWithACapturedPayment_isRefundedOnce_withAKeyFromTheOrder() {
        // was rejected_isAFailureWithoutAskingTheGateway: the customer's money was kept
        gateway.givenIntent("pi_789", "succeeded", 129_999L, "eur");
        InventoryResult result = rejected(4L, "pi_789");

        service.process(result);

        assertThat(gateway.verifiedIntents()).containsExactly("pi_789");
        assertThat(gateway.refundRequests()).containsExactly(new FakePaymentGateway.RefundRequest("pi_789", "refund-order-4"));
        assertThat(transactions.findAll()).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.orderId()).isEqualTo(4L);
            assertThat(t.paymentIntentId()).isEqualTo("pi_789");
            assertThat(t.refundId()).isEqualTo("re_1_pi_789");
        });
        assertThat(auditTrail.processed()).singleElement()
                .satisfies(audit -> assertThat(audit.transaction().status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(auditTrail.refunded()).singleElement().satisfies(audit -> {
            assertThat(audit.transaction().refundId()).isEqualTo("re_1_pi_789");
            assertThat(audit.userEmail()).isEqualTo("user@test.com");
        });
        // the saga's event is unchanged: still a plain FAILED
        assertThat(publisher.published()).containsExactly(
                new RecordingPaymentResultPublisher.Published(result, PaymentStatus.FAILED));
    }

    @Test
    void rejectedWithoutIntent_isAFailureWithoutAskingTheGateway() {
        service.process(rejected(4L, null));

        assertThat(gateway.verifiedIntents()).isEmpty();
        assertThat(gateway.refundRequests()).isEmpty();
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void rejectedWithADeclinedOrUnknownIntent_hasNothingToRefund() {
        gateway.givenIntent("pi_declined", "requires_payment_method", 129_999L, "eur");

        service.process(rejected(4L, "pi_declined"));
        service.process(rejected(5L, "pi_unknown"));

        assertThat(gateway.verifiedIntents()).containsExactly("pi_declined", "pi_unknown");
        assertThat(gateway.refundRequests()).isEmpty();
        assertThat(transactions.findAll()).extracting(PaymentTransaction::status)
                .containsOnly(PaymentStatus.FAILED);
    }

    @Test
    void refundedByHandAlready_isRecordedAsRefunded() {
        gateway.givenIntent("pi_789", "succeeded", 129_999L, "eur");
        gateway.refundedOutsideTheShop("pi_789");

        service.process(rejected(4L, "pi_789"));

        assertThat(transactions.findByOrderId(4L)).hasValueSatisfying(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.refundId()).isEqualTo(PaymentRefund.ALREADY_REFUNDED);
        });
        assertThat(auditTrail.refunded()).hasSize(1);
        assertThat(auditTrail.refundFailed()).isEmpty();
    }

    @Test
    void refundRefused_isAFailure_auditedForAManualRefund_andKeepsTheIntent() {
        gateway.givenIntent("pi_789", "succeeded", 129_999L, "eur");
        gateway.refundRefused("pi_789", "charge_disputed");

        service.process(rejected(4L, "pi_789"));

        assertThat(transactions.findByOrderId(4L)).hasValueSatisfying(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.refundId()).isNull();
            // the money is still captured for this order: the intent cannot pay for another one
            assertThat(t.paymentIntentId()).isEqualTo("pi_789");
        });
        assertThat(auditTrail.refunded()).isEmpty();
        assertThat(auditTrail.refundFailed()).singleElement().satisfies(audit -> {
            assertThat(audit.errorCode()).isEqualTo("charge_disputed");
            assertThat(audit.transaction().orderId()).isEqualTo(4L);
        });
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void refundUnavailable_propagatesAndRecordsNothing_thenTheRetryRefundsOnceWithTheSameKey() {
        gateway.givenIntent("pi_789", "succeeded", 129_999L, "eur");
        gateway.refundsUnavailableFor(1);
        InventoryResult result = rejected(4L, "pi_789");

        assertThatThrownBy(() -> service.process(result)).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(transactions.findAll()).isEmpty();
        assertThat(publisher.published()).isEmpty();

        service.process(result);

        assertThat(gateway.refundRequests()).extracting(FakePaymentGateway.RefundRequest::idempotencyKey)
                .containsExactly("refund-order-4", "refund-order-4");
        assertThat(gateway.refunds()).hasSize(1);
        assertThat(transactions.findByOrderId(4L)).hasValueSatisfying(t -> assertThat(t.refunded()).isTrue());
    }

    @Test
    void redeliveryAfterARefund_republishesWithoutRefundingAgain() {
        gateway.givenIntent("pi_789", "succeeded", 129_999L, "eur");
        InventoryResult result = rejected(4L, "pi_789");
        service.process(result);

        service.process(result);

        assertThat(gateway.refundRequests()).hasSize(1);
        assertThat(auditTrail.refunded()).hasSize(1);
        assertThat(publisher.published()).extracting(RecordingPaymentResultPublisher.Published::status)
                .containsExactly(PaymentStatus.FAILED, PaymentStatus.FAILED);
    }

    @Test
    void intentRecordedForAnotherOrderDuringTheRefund_keepsTheRefund_withoutTheIntent() {
        gateway.givenIntent("pi_789", "succeeded", 129_999L, "eur");
        transactions.insertConcurrentlyBeforeNextSave(PaymentTransaction.paid(9L, new BigDecimal("1299.99"), "pi_789"));

        service.process(rejected(4L, "pi_789"));

        assertThat(transactions.findByOrderId(4L)).hasValueSatisfying(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.paymentIntentId()).isNull();
            assertThat(t.refundId()).isEqualTo("re_1_pi_789");
        });
    }

    @Test
    void effects_ofARefund_happenInOrder_refundSaveAuditsThenPublish() {
        gateway.givenIntent("pi_789", "succeeded", 129_999L, "eur");

        service.process(rejected(4L, "pi_789"));

        assertThat(effectLog.effects()).containsExactly("refund pi_789", "save FAILED", "audit FAILED",
                "audit REFUNDED", "publish FAILED");
    }

    @Test
    void effects_happenInOrder_saveThenAuditThenPublish() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");

        service.process(approved(1L, "pi_123", null));

        assertThat(effectLog.effects()).containsExactly("save SUCCESS", "audit SUCCESS", "publish SUCCESS");
    }

    @Test
    void redelivery_republishesTheStoredOutcomeOnly() {
        // was redelivery_recordsAndPublishesASecondTime_preservedBug: a redelivery used to record, audit and
        // publish a second time
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        InventoryResult result = approved(1L, "pi_123", null);
        service.process(result);

        service.process(result);

        assertThat(gateway.verifiedIntents()).containsExactly("pi_123");
        assertThat(transactions.findAll()).extracting(PaymentTransaction::orderId).containsExactly(1L);
        assertThat(auditTrail.processed()).hasSize(1);
        assertThat(effectLog.effects()).containsExactly(
                "save SUCCESS", "audit SUCCESS", "publish SUCCESS", "publish SUCCESS");
        assertThat(publisher.published()).containsExactly(
                new RecordingPaymentResultPublisher.Published(result, PaymentStatus.SUCCESS),
                new RecordingPaymentResultPublisher.Published(result, PaymentStatus.SUCCESS));
    }

    @Test
    void alreadyRecorded_republishesTheStoredStatusEvenIfTheGatewayNowAnswersDifferently() {
        // a crash between saving and publishing: the retry publishes what was decided, without asking again
        transactions.save(PaymentTransaction.record(1L, new BigDecimal("1299.99"), false));
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");

        service.process(approved(1L, "pi_123", null));

        assertThat(gateway.verifiedIntents()).isEmpty();
        assertThat(transactions.findAll()).hasSize(1);
        assertThat(auditTrail.processed()).isEmpty();
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void concurrentDelivery_losesTheRaceAndRepublishesTheWinnersOutcome() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        transactions.insertConcurrentlyBeforeNextSave(
                PaymentTransaction.paid(1L, new BigDecimal("1299.99"), "pi_123"));

        service.process(approved(1L, "pi_123", null));

        assertThat(transactions.findAll()).hasSize(1);
        assertThat(auditTrail.processed()).isEmpty();
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.SUCCESS));
    }

    @Test
    void saveFailure_propagatesBeforeAuditAndPublish() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        transactions.failWith(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.process(approved(1L, "pi_123", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
        assertThat(effectLog.effects()).isEmpty();
    }

    @Test
    void publishFailure_propagatesAfterSaveAndAudit() {
        gateway.givenIntent("pi_123", "succeeded", 129_999L, "eur");
        publisher.failWith(new IllegalStateException("cannot serialize"));

        assertThatThrownBy(() -> service.process(approved(1L, "pi_123", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(effectLog.effects()).containsExactly("save SUCCESS", "audit SUCCESS");
    }

    private static InventoryResult rejected(Long orderId, String paymentIntentId) {
        return new InventoryResult(orderId, "REJECTED", "MacBook Pro", 0, new BigDecimal("1299.99"),
                new BigDecimal("1299.99"), "user@test.com", paymentIntentId, null);
    }

    private static InventoryResult approved(Long orderId, String paymentIntentId, List<InventoryResult.Item> items) {
        return new InventoryResult(orderId, "APPROVED", "MacBook Pro", 1, new BigDecimal("1299.99"),
                new BigDecimal("1299.99"), "user@test.com", paymentIntentId, items);
    }
}
