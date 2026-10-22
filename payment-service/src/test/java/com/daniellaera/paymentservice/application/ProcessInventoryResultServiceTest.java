package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.application.fakes.EffectLog;
import com.daniellaera.paymentservice.application.fakes.FakePaymentGateway;
import com.daniellaera.paymentservice.application.fakes.InMemoryTransactionRepository;
import com.daniellaera.paymentservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.paymentservice.application.fakes.RecordingPaymentResultPublisher;
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
    private final FakePaymentGateway gateway = new FakePaymentGateway();
    private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository(effectLog);
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail(effectLog);
    private final RecordingPaymentResultPublisher publisher = new RecordingPaymentResultPublisher(effectLog);
    private final ProcessInventoryResultService service =
            new ProcessInventoryResultService(gateway, transactions, auditTrail, publisher);

    @Test
    void approvedAndIntentSucceeded_recordsAuditsAndPublishesSuccess() {
        gateway.givenIntent("pi_123", "succeeded");
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
        gateway.givenIntent("pi_999", "succeeded");
        List<InventoryResult.Item> items = List.of(
                new InventoryResult.Item("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")));

        service.process(approved(5L, "pi_999", items));

        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.result().items()).isEqualTo(items));
    }

    @Test
    void approvedAndIntentNotSucceeded_recordsAndPublishesFailure() {
        gateway.givenIntent("pi_456", "requires_payment_method");

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
        // the fake answers false for an intent it does not know, as the Stripe adapter does on a StripeException
        service.process(approved(2L, "pi_unknown", null));

        assertThat(gateway.verifiedIntents()).containsExactly("pi_unknown");
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void approvedWithoutIntent_isAutoApprovedWithoutAskingTheGateway() {
        // known bug, kept on purpose (backlog: refuse orders with no intent)
        service.process(approved(3L, null, null));

        assertThat(gateway.verifiedIntents()).isEmpty();
        assertThat(transactions.findAll()).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(PaymentStatus.SUCCESS));
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.SUCCESS));
    }

    @Test
    void approvedWithBlankIntent_isAutoApprovedToo() {
        service.process(approved(3L, "  ", null));

        assertThat(gateway.verifiedIntents()).isEmpty();
        assertThat(transactions.findAll()).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(PaymentStatus.SUCCESS));
    }

    @Test
    void rejected_isAFailureWithoutAskingTheGateway() {
        service.process(new InventoryResult(4L, "REJECTED", "MacBook Pro", 0, new BigDecimal("1299.99"),
                new BigDecimal("1299.99"), "user@test.com", "pi_789", null));

        assertThat(gateway.verifiedIntents()).isEmpty();
        assertThat(transactions.findAll()).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(t.orderId()).isEqualTo(4L);
        });
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void effects_happenInOrder_saveThenAuditThenPublish() {
        gateway.givenIntent("pi_123", "succeeded");

        service.process(approved(1L, "pi_123", null));

        assertThat(effectLog.effects()).containsExactly("save SUCCESS", "audit SUCCESS", "publish SUCCESS");
    }

    @Test
    void redelivery_republishesTheStoredOutcomeOnly() {
        // was redelivery_recordsAndPublishesASecondTime_preservedBug: a redelivery used to record, audit and
        // publish a second time
        gateway.givenIntent("pi_123", "succeeded");
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
        gateway.givenIntent("pi_123", "succeeded");

        service.process(approved(1L, "pi_123", null));

        assertThat(gateway.verifiedIntents()).isEmpty();
        assertThat(transactions.findAll()).hasSize(1);
        assertThat(auditTrail.processed()).isEmpty();
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void concurrentDelivery_losesTheRaceAndRepublishesTheWinnersOutcome() {
        gateway.givenIntent("pi_123", "succeeded");
        transactions.insertConcurrentlyBeforeNextSave(
                new PaymentTransaction(null, 1L, new BigDecimal("1299.99"), PaymentStatus.SUCCESS, null));

        service.process(approved(1L, "pi_123", null));

        assertThat(transactions.findAll()).hasSize(1);
        assertThat(auditTrail.processed()).isEmpty();
        assertThat(publisher.published()).singleElement()
                .satisfies(published -> assertThat(published.status()).isEqualTo(PaymentStatus.SUCCESS));
    }

    @Test
    void saveFailure_propagatesBeforeAuditAndPublish() {
        transactions.failWith(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.process(approved(1L, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
        assertThat(effectLog.effects()).isEmpty();
    }

    @Test
    void publishFailure_propagatesAfterSaveAndAudit() {
        publisher.failWith(new IllegalStateException("cannot serialize"));

        assertThatThrownBy(() -> service.process(approved(1L, null, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(effectLog.effects()).containsExactly("save SUCCESS", "audit SUCCESS");
    }

    private static InventoryResult approved(Long orderId, String paymentIntentId, List<InventoryResult.Item> items) {
        return new InventoryResult(orderId, "APPROVED", "MacBook Pro", 1, new BigDecimal("1299.99"),
                new BigDecimal("1299.99"), "user@test.com", paymentIntentId, items);
    }
}
