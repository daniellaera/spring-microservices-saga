package com.daniellaera.paymentservice.adapter.in.messaging;

import com.daniellaera.paymentservice.TestcontainersConfiguration;
import com.daniellaera.paymentservice.adapter.out.persistence.TransactionJpaRepository;
import com.daniellaera.paymentservice.domain.model.IntentVerification;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayUnavailableException;
import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;
import com.daniellaera.paymentservice.support.TopicReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the listener container does with an inventory result whose processing keeps failing (here: Stripe is
 * unavailable): the retry topics
 * (@RetryableTopic on the listener), then inventory-topic-dlt, whose handler logs it once and audits
 * PAYMENT_DEAD_LETTERED. Nothing is recorded or published for the order, which stays PENDING. The backoff is
 * shortened here (fixed 3 s); the production values are pinned by InventoryResultEventListenerTest.
 */
@SpringBootTest(properties = {
        "payment.retry.attempts=3",
        "payment.retry.delay=3000",
        "payment.retry.multiplier=1.0",
        "payment.retry.max-delay=3000"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(OutputCaptureExtension.class)
class InventoryResultRetryITTest {

    // spring.kafka.retry.topic.attempts: the first delivery included
    private static final int ATTEMPTS = 3;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TransactionJpaRepository transactionRepository;

    @Autowired
    private KafkaContainer kafka;

    @Test
    void failingEveryAttempt_endsInTheDeadLetterTopic_handledOnce_andNothingIsRecorded(CapturedOutput output) {
        when(paymentGateway.verifyIntent("pi_801")).thenThrow(new PaymentGatewayUnavailableException("Stripe unavailable: 503", null));

        kafkaTemplate.send("inventory-topic", approved(801L, "pi_801"));

        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(output.getAll()).contains("DLT received failed message: {\"orderId\":801,"));
        assertThat(messagesOn("inventory-topic-dlt")).anyMatch(message -> message.contains("\"orderId\":801"));
        // the handler ran once and its offset was committed: no retry loop on the dead-letter topic
        assertThat(output.getAll().split(Pattern.quote("DLT received failed message: {\"orderId\":801,"), -1))
                .hasSize(2);
        assertThat(messagesOn("audit-topic")).filteredOn(message -> message.contains("PAYMENT_DEAD_LETTERED"))
                .singleElement().satisfies(audit -> assertThat(audit)
                        .contains("\"entityId\":\"801\"").contains("Stripe unavailable: 503"));
        verify(paymentGateway, times(ATTEMPTS)).verifyIntent("pi_801");
        assertThat(transactionRepository.findByOrderId(801L)).isEmpty();
        assertThat(messagesOn("payment-topic")).noneMatch(message -> message.contains("\"orderId\":801"));
    }

    @Test
    void aRetryingResult_doesNotHoldBackTheNextOne() {
        // 805's retries wait until 806 is verified: on the retry topic's own consumer that holds back nothing, while
        // retried in place they would block inventory-topic, and 806 behind them, for longer than the test waits
        CountDownLatch verified806 = new CountDownLatch(1);
        when(paymentGateway.verifyIntent("pi_805"))
                .thenThrow(new PaymentGatewayUnavailableException("Stripe unavailable: 503", null))
                .thenAnswer(invocation -> {
                    verified806.await(120, TimeUnit.SECONDS);
                    throw new PaymentGatewayUnavailableException("Stripe unavailable: 503", null);
                });
        when(paymentGateway.verifyIntent("pi_806")).thenAnswer(invocation -> {
            verified806.countDown();
            return Optional.of(new IntentVerification("succeeded", 1000L, "eur"));
        });

        kafkaTemplate.send("inventory-topic", approved(805L, "pi_805"));
        kafkaTemplate.send("inventory-topic", approved(806L, "pi_806"));

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(transactionRepository.findByOrderId(806L))
                .hasValueSatisfying(t -> assertThat(t.getStatus()).isEqualTo(PaymentStatus.SUCCESS)));
        assertThat(transactionRepository.findByOrderId(805L)).isEmpty();
    }

    @Test
    void stripeBackBeforeTheLastAttempt_theRetryPays() {
        when(paymentGateway.verifyIntent("pi_802"))
                .thenThrow(new PaymentGatewayUnavailableException("Stripe unavailable: 503", null))
                .thenReturn(Optional.of(new IntentVerification("succeeded", 1000L, "eur")));

        kafkaTemplate.send("inventory-topic", approved(802L, "pi_802"));

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(transactionRepository.findByOrderId(802L))
                .hasValueSatisfying(t -> assertThat(t.getStatus()).isEqualTo(PaymentStatus.SUCCESS)));
        verify(paymentGateway, times(2)).verifyIntent("pi_802");
    }

    private static String approved(long orderId, String paymentIntentId) {
        return "{\"orderId\":" + orderId + ",\"status\":\"APPROVED\",\"productName\":\"Item\",\"quantity\":1,"
                + "\"price\":10.00,\"totalAmount\":10.00,\"userEmail\":\"user@test.com\","
                + "\"paymentIntentId\":\"" + paymentIntentId + "\",\"items\":[]}";
    }

    private List<String> messagesOn(String topic) {
        return TopicReader.readAll(kafka.getBootstrapServers(), topic);
    }
}
