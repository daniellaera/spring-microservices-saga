package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import com.daniellaera.inventoryservice.domain.port.in.CompensateStockUseCase;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockUseCase;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

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
import static org.mockito.Mockito.when;

/**
 * Both listeners on a real broker, with a use case that fails every time: the record is retried on this service's
 * retry topics, then dead-lettered, and the class's @DltHandler handles it exactly once and commits it. Spring
 * Kafka's default DLT handler, which spring.kafka.retry.topic.* would use instead, failed on every record under
 * ack-mode: record and re-handled it forever. The backoff is shortened here (fixed 3 s); the production values are
 * pinned by OrderEventListenerTest and PaymentEventListenerTest.
 */
@SpringBootTest(properties = {
        "spring.kafka.admin.auto-create=true",
        "inventory.retry.attempts=3",
        "inventory.retry.delay=3000",
        "inventory.retry.multiplier=1.0",
        "inventory.retry.max-delay=3000"
})
@Import({TestcontainersConfiguration.class, DeadLetterITTest.Broker.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(OutputCaptureExtension.class)
class DeadLetterITTest {

    private static final int ATTEMPTS = 3;

    @TestConfiguration(proxyBeanMethods = false)
    static class Broker {
        @Bean
        @ServiceConnection
        KafkaContainer kafkaContainer() {
            return new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));
        }
    }

    @MockitoBean
    private ReserveStockUseCase reserveStockUseCase;

    @MockitoBean
    private CompensateStockUseCase compensateStockUseCase;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Test
    void anOrderFailingEveryAttempt_isDeadLetteredAndHandledOnce(CapturedOutput output) throws Exception {
        when(reserveStockUseCase.reserve(argThat(command -> command.orderId() == 901L)))
                .thenThrow(new IllegalStateException("database unavailable"));

        kafkaTemplate.send("orders-topic", "{\"orderId\":901,\"productName\":\"Lamp\",\"quantity\":1}");

        String handled = "DLT received failed message: {\"orderId\":901,";
        // the whole line: the capture may hold a line still being written
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(output.getAll())
                .contains(handled + "\"productName\":\"Lamp\",\"quantity\":1} error: Listener failed; database unavailable"));
        assertHandledOnceAndCommitted(output, handled, "orders-topic-inventory-dlt", "inventory-group-inventory-dlt");
        verify(reserveStockUseCase, times(ATTEMPTS)).reserve(argThat(command -> command.orderId() == 901L));
    }

    @Test
    void anUnreadableOrder_skipsTheRetries(CapturedOutput output) {
        kafkaTemplate.send("orders-topic", "not json 902");

        // before the first 3 s backoff could even end: JacksonException is excluded from the retries
        await().atMost(Duration.ofMillis(2500))
                .untilAsserted(() -> assertThat(output.getAll()).contains("DLT received failed message: not json 902 error: "));
        verify(reserveStockUseCase, never()).reserve(any());
    }

    @Test
    void aCompensationFailingEveryAttempt_isDeadLetteredAndHandledOnce(CapturedOutput output) throws Exception {
        doThrow(new IllegalStateException("database unavailable"))
                .when(compensateStockUseCase).compensate(argThat(outcome -> outcome.orderId() == 903L));

        kafkaTemplate.send("payment-topic", "{\"orderId\":903,\"status\":\"FAILED\",\"userEmail\":\"user@test.com\"}");

        String handled = "DLT received failed message: {\"orderId\":903,";
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(output.getAll())
                .contains(handled + "\"status\":\"FAILED\",\"userEmail\":\"user@test.com\"} error: Listener failed; database unavailable"));
        assertHandledOnceAndCommitted(output, handled, "payment-topic-inventory-dlt",
                "inventory-compensation-group-inventory-dlt");
        verify(compensateStockUseCase, times(ATTEMPTS)).compensate(argThat(outcome -> outcome.orderId() == 903L));
    }

    // Still handled once a few seconds later, and the DLT consumer's committed offset is at the end of the topic
    private void assertHandledOnceAndCommitted(CapturedOutput output, String handled, String dltTopic, String dltGroup)
            throws Exception {
        TopicPartition partition = new TopicPartition(dltTopic, 0);
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            long end = admin.listOffsets(Map.of(partition, OffsetSpec.latest())).all().get().get(partition).offset();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                OffsetAndMetadata committed = admin.listConsumerGroupOffsets(dltGroup)
                        .partitionsToOffsetAndMetadata().get().get(partition);
                assertThat(committed).isNotNull();
                assertThat(committed.offset()).isEqualTo(end);
            });
        }
        await().pollDelay(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(6)).untilAsserted(() ->
                assertThat(output.getAll().split(Pattern.quote(handled), -1)).hasSize(2));
    }
}
