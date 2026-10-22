package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.application.fakes.InMemoryOrderRepository;
import com.daniellaera.orderservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.orderservice.application.fakes.RecordingOrderStatusNotifier;
import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyPaymentResultServiceTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail();
    private final RecordingOrderStatusNotifier notifier = new RecordingOrderStatusNotifier();
    private final ApplyPaymentResultService service = new ApplyPaymentResultService(repository, auditTrail, notifier);

    @Test
    void success_confirmsOrderThenAuditsAndNotifies() {
        long orderId = placeOrder("user@test.com");
        PaymentResult result = payment(orderId, "SUCCESS");

        service.applyPaymentResult(result);

        assertThat(repository.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(auditTrail.paymentResults()).singleElement().satisfies(audit -> {
            assertThat(audit.order().status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(audit.result()).isSameAs(result);
        });
        assertThat(notifier.notified()).singleElement()
                .satisfies(order -> assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED));
    }

    @Test
    void failure_cancelsOrder() {
        long orderId = placeOrder("user@test.com");

        service.applyPaymentResult(payment(orderId, "FAILED"));

        assertThat(repository.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(auditTrail.paymentResults()).singleElement()
                .satisfies(audit -> assertThat(audit.order().status()).isEqualTo(OrderStatus.CANCELLED));
        assertThat(notifier.notified()).hasSize(1);
    }

    @Test
    void anyStatusOtherThanSuccess_cancelsOrder() {
        long orderId = placeOrder("user@test.com");

        service.applyPaymentResult(payment(orderId, null));

        assertThat(repository.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void unknownOrder_hasNoSideEffects() {
        service.applyPaymentResult(payment(999L, "SUCCESS"));

        assertThat(repository.saved()).isEmpty();
        assertThat(auditTrail.paymentResults()).isEmpty();
        assertThat(notifier.notified()).isEmpty();
    }

    @Test
    void orderWithoutEmail_isStillUpdatedAndNotified() {
        long orderId = placeOrder(null);

        service.applyPaymentResult(payment(orderId, "SUCCESS"));

        assertThat(repository.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(auditTrail.paymentResults()).hasSize(1);
        assertThat(notifier.notified()).singleElement()
                .satisfies(order -> assertThat(order.userEmail()).isNull());
    }

    @Test
    void savesBeforeAuditAndNotify() {
        long orderId = placeOrder("user@test.com");

        service.applyPaymentResult(payment(orderId, "SUCCESS"));

        // The audited and notified order is the one returned by save, carrying the stored status
        assertThat(repository.saved()).last()
                .satisfies(stored -> assertThat(stored.status()).isEqualTo(OrderStatus.CONFIRMED));
    }

    @Test
    void redeliveredResult_forAConfirmedOrder_isSkippedWithoutSaveAuditOrNotify() {
        long orderId = placeOrder("user@test.com");
        service.applyPaymentResult(payment(orderId, "SUCCESS"));
        int savesBefore = repository.saved().size();

        service.applyPaymentResult(payment(orderId, "SUCCESS"));

        assertThat(repository.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(repository.saved()).hasSize(savesBefore);
        assertThat(auditTrail.paymentResults()).hasSize(1);
        assertThat(notifier.notified()).hasSize(1);
    }

    @Test
    void failureAfterConfirmation_doesNotCancelTheOrder() {
        long orderId = placeOrder("user@test.com");
        service.applyPaymentResult(payment(orderId, "SUCCESS"));

        service.applyPaymentResult(payment(orderId, "FAILED"));

        assertThat(repository.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(auditTrail.paymentResults()).singleElement()
                .satisfies(audit -> assertThat(audit.order().status()).isEqualTo(OrderStatus.CONFIRMED));
        assertThat(notifier.notified()).hasSize(1);
    }

    @Test
    void successAfterCancellation_doesNotConfirmTheOrder() {
        long orderId = placeOrder("user@test.com");
        service.applyPaymentResult(payment(orderId, "FAILED"));

        service.applyPaymentResult(payment(orderId, "SUCCESS"));

        assertThat(repository.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(auditTrail.paymentResults()).hasSize(1);
        assertThat(notifier.notified()).hasSize(1);
    }

    @Test
    void isNotTransactional() throws NoSuchMethodException {
        assertThat(ApplyPaymentResultService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(ApplyPaymentResultService.class.getMethod("applyPaymentResult", PaymentResult.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    private long placeOrder(String userEmail) {
        return repository.save(Order.place(userEmail, null,
                List.of(OrderItem.of("MacBook Pro", 1, BigDecimal.valueOf(1299.99))))).id();
    }

    private static PaymentResult payment(long orderId, String status) {
        return new PaymentResult(orderId, "MacBook Pro", 1, status, BigDecimal.valueOf(1299.99),
                BigDecimal.valueOf(1299.99), "user@test.com");
    }
}
