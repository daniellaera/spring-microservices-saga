package com.daniellaera.paymentservice.adapter.out.messaging;

import com.daniellaera.paymentservice.TestcontainersConfiguration;
import com.daniellaera.paymentservice.domain.model.IntentVerification;
import com.daniellaera.paymentservice.domain.model.PaymentRefund;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;
import com.daniellaera.paymentservice.support.TopicReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * audit-trail-service orders a saga's timeline as it receives the events, so the payment's outcome must be on the
 * audit topic before inventory-service and order-service can react to the payment result (e2e run 295 showed
 * STOCK_RESTORED before PAYMENT_FAILED). Here every @Async task is held until the test ends: an outcome event sent
 * through the executor would be missing when the result appears.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, OutcomeAuditOrderITTest.HeldAsyncTasks.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutcomeAuditOrderITTest {

    private static final CountDownLatch RELEASE_ASYNC_TASKS = new CountDownLatch(1);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaContainer kafka;

    @AfterAll
    static void releaseAsyncTasks() {
        RELEASE_ASYNC_TASKS.countDown();
    }

    @Test
    void aDeclinedPayment_isAuditedBeforeItsResultIsPublished() {
        when(paymentGateway.verifyIntent("pi_821"))
                .thenReturn(Optional.of(new IntentVerification("requires_payment_method", 1000L, "eur")));

        kafkaTemplate.send("inventory-topic", approved(821L, "pi_821"));

        assertThat(auditedWhenTheResultAppears(821L)).containsExactly("PAYMENT_FAILED");
    }

    @Test
    void aRefundedPayment_isAuditedBeforeItsResultIsPublished() {
        // the intent captured more than the order costs: refunded, and the order fails
        when(paymentGateway.verifyIntent("pi_822"))
                .thenReturn(Optional.of(new IntentVerification("succeeded", 2000L, "eur")));
        when(paymentGateway.refund(anyString(), anyString())).thenReturn(new PaymentRefund("re_822", "succeeded"));

        kafkaTemplate.send("inventory-topic", approved(822L, "pi_822"));

        assertThat(auditedWhenTheResultAppears(822L)).containsExactly("PAYMENT_FAILED", "PAYMENT_REFUNDED");
    }

    // The order's audit event types on audit-topic, read as soon as its payment result is on payment-topic
    private List<String> auditedWhenTheResultAppears(long orderId) {
        String order = "\"orderId\":" + orderId + ",";
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(50))
                .until(() -> TopicReader.readAll(kafka.getBootstrapServers(), "payment-topic").stream()
                        .anyMatch(message -> message.contains(order)));
        return TopicReader.readAll(kafka.getBootstrapServers(), "audit-topic").stream()
                .map(JSON::readTree)
                .filter(audit -> audit.path("correlationId").asString().equals(String.valueOf(orderId)))
                .map(audit -> audit.path("eventType").asString())
                .toList();
    }

    private static String approved(long orderId, String paymentIntentId) {
        return "{\"orderId\":" + orderId + ",\"status\":\"APPROVED\",\"productName\":\"Item\",\"quantity\":1,"
                + "\"price\":10.00,\"totalAmount\":10.00,\"userEmail\":\"user@test.com\","
                + "\"paymentIntentId\":\"" + paymentIntentId + "\",\"items\":[]}";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HeldAsyncTasks {

        // replaces Boot's applicationTaskExecutor, which @Async uses
        @Bean
        Executor taskExecutor() {
            Executor executor = Executors.newCachedThreadPool();
            return task -> executor.execute(() -> {
                try {
                    RELEASE_ASYNC_TASKS.await(2, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                task.run();
            });
        }
    }
}
