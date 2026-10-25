package com.daniellaera.orderservice.adapter.in.messaging;

import com.daniellaera.orderservice.TestcontainersConfiguration;
import com.daniellaera.orderservice.domain.port.in.ApplyPaymentResultUseCase;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
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
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The payment result listener on a real broker, with a use case that fails every time: the result is retried on
 * order-service's own retry topics, then dead-lettered, and the class's @DltHandler handles it exactly once and
 * commits it. Spring Kafka's default DLT handler, which spring.kafka.retry.topic.* would use instead, failed on every
 * record under ack-mode: record and re-handled it forever. The backoff is shortened here (fixed 3 s); the production
 * values are pinned by PaymentEventListenerTest.
 */
@SpringBootTest(properties = {
        "order.retry.attempts=3",
        "order.retry.delay=3000",
        "order.retry.multiplier=1.0",
        "order.retry.max-delay=3000"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(OutputCaptureExtension.class)
class DeadLetterITTest {

    private static final int ATTEMPTS = 3;

    @MockitoBean
    private ApplyPaymentResultUseCase applyPaymentResultUseCase;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Test
    void aPaymentResultFailingEveryAttempt_isDeadLetteredAndHandledOnce(CapturedOutput output) throws Exception {
        doThrow(new IllegalStateException("database unavailable"))
                .when(applyPaymentResultUseCase).applyPaymentResult(argThat(result -> result.orderId() == 911L));

        kafkaTemplate.send("payment-topic", "{\"orderId\":911,\"status\":\"SUCCESS\",\"userEmail\":\"user@test.com\"}");

        String handled = "DLT received failed message: {\"orderId\":911,";
        // the whole line: the capture may hold a line still being written
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(output.getAll())
                .contains(handled + "\"status\":\"SUCCESS\",\"userEmail\":\"user@test.com\"} error: Listener failed; database unavailable"));

        TopicPartition partition = new TopicPartition("payment-topic-order-dlt", 0);
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            long end = admin.listOffsets(Map.of(partition, OffsetSpec.latest())).all().get().get(partition).offset();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                OffsetAndMetadata committed = admin.listConsumerGroupOffsets("order-group-order-dlt")
                        .partitionsToOffsetAndMetadata().get().get(partition);
                assertThat(committed).isNotNull();
                assertThat(committed.offset()).isEqualTo(end);
            });
        }
        // still handled once a few seconds later: no loop on the dead-letter topic
        await().pollDelay(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(6)).untilAsserted(() ->
                assertThat(output.getAll().split(Pattern.quote(handled), -1)).hasSize(2));
        verify(applyPaymentResultUseCase, times(ATTEMPTS)).applyPaymentResult(argThat(result -> result.orderId() == 911L));
    }

    @Test
    void anUnreadablePaymentResult_skipsTheRetries(CapturedOutput output) {
        kafkaTemplate.send("payment-topic", "not json 912");

        // before the first 3 s backoff could even end: JacksonException is excluded from the retries
        await().atMost(Duration.ofMillis(2500))
                .untilAsserted(() -> assertThat(output.getAll()).contains("DLT received failed message: not json 912 error: "));
        verify(applyPaymentResultUseCase, never()).applyPaymentResult(any());
    }
}
