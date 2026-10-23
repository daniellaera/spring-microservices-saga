package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.TestcontainersConfiguration;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs the scheduled relay against a real Kafka that becomes unreachable (container paused) and comes back:
 * nothing is marked published while Kafka is down, no row counts an attempt or fails however long the outage
 * lasts, and once it is back every row goes out, in order. A row that can never be sent fails after max-attempts
 * ticks, is audited, and does not hold back the rows after it.
 */
@SpringBootTest(properties = {
        // the prod producer timeouts (config-server order-service.yml), so a send fails within seconds
        "spring.kafka.producer.properties.max.block.ms=1000",
        "spring.kafka.producer.properties.request.timeout.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=2000",
        "outbox.relay.send-timeout=3s",
        "outbox.relay.max-attempts=2"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxDeliveryITTest {

    @Autowired
    private OutboxEventJpaRepository outboxRepository;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private ObjectMapper objectMapper;

    private boolean paused;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @AfterEach
    void unpauseKafka() {
        if (paused) {
            resumeKafka();
        }
    }

    @Test
    void kafkaDown_nothingIsMarkedPublished_andEveryRowGoesOutInOrderOnceItIsBack() {
        pauseKafka();
        UUID first = outboxRow(9001L);
        UUID second = outboxRow(9002L);

        // two relay ticks (every 5 s) with Kafka unreachable: as many as max-attempts, and still nothing fails
        await().during(Duration.ofSeconds(12)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRepository.findAll()).allSatisfy(row -> {
                    assertThat(row.isPublished()).isFalse();
                    assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(row.getAttempts()).isZero();
                }));

        resumeKafka();

        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(outboxRepository.findAllById(List.of(first, second)))
                        .hasSize(2).allMatch(OutboxEventJpaEntity::isPublished));
        // at least once, and the first row before the second
        List<Long> delivered = ordersTopicOrderIds(9001L, 9002L);
        assertThat(delivered).contains(9001L, 9002L);
        assertThat(delivered.indexOf(9001L)).isLessThan(delivered.indexOf(9002L));
    }

    @Test
    void unreadableRow_failsAfterMaxAttempts_isAudited_andDoesNotHoldBackLaterRows() {
        OutboxEventJpaEntity poison = new OutboxEventJpaEntity();
        poison.setAggregateId(9101L);
        poison.setEventType("ORDER_CREATED");
        poison.setPayload("{not json");
        UUID poisonId = outboxRepository.save(poison).getId();
        UUID later = outboxRow(9102L);

        // the row after it goes out on the first tick
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxRepository.findById(later)).get()
                        .returns(OutboxStatus.PUBLISHED, OutboxEventJpaEntity::getStatus));
        assertThat(ordersTopicOrderIds(9102L)).contains(9102L);

        // two ticks later it is FAILED, and stays so
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRepository.findById(poisonId)).get()
                        .returns(OutboxStatus.FAILED, OutboxEventJpaEntity::getStatus));
        await().during(Duration.ofSeconds(6)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(outboxRepository.findById(poisonId)).get().satisfies(row -> {
                    assertThat(row.getAttempts()).isEqualTo(2);
                    assertThat(row.isPublished()).isFalse();
                    assertThat(row.getLastError()).startsWith("unreadable payload: ");
                }));

        Map<?, ?> audit = auditMessage("OUTBOX_EVENT_FAILED", "9101");
        assertThat(audit.get("entityType")).isEqualTo("ORDER");
        assertThat(audit.get("userEmail")).isEqualTo("system");
        assertThat(objectMapper.readValue((String) audit.get("payload"), Map.class))
                .containsEntry("eventType", "ORDER_CREATED")
                .containsEntry("attempts", 2);
    }

    private UUID outboxRow(long orderId) {
        OutboxEventJpaEntity row = new OutboxEventJpaEntity();
        row.setAggregateId(orderId);
        row.setEventType("ORDER_CREATED");
        row.setPayload(objectMapper.writeValueAsString(new OrderEvent(orderId, "Item", 1, BigDecimal.TEN,
                BigDecimal.TEN, "user@test.com", "pi_" + orderId, List.of())));
        return outboxRepository.save(row).getId();
    }

    private List<Long> ordersTopicOrderIds(Long... expected) {
        try (KafkaConsumer<String, String> consumer = consumer("orders-topic")) {
            List<Long> orderIds = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    orderIds.add(objectMapper.readValue(record.value(), OrderEvent.class).orderId());
                }
                return orderIds.containsAll(List.of(expected));
            });
            return orderIds;
        }
    }

    private Map<?, ?> auditMessage(String eventType, String entityId) {
        try (KafkaConsumer<String, String> consumer = consumer("audit-topic")) {
            List<Map<?, ?>> found = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    Map<?, ?> message = objectMapper.readValue(record.value(), Map.class);
                    if (eventType.equals(message.get("eventType")) && entityId.equals(message.get("entityId"))) {
                        found.add(message);
                    }
                }
                return !found.isEmpty();
            });
            return found.getFirst();
        }
    }

    private KafkaConsumer<String, String> consumer(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private void pauseKafka() {
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        paused = true;
    }

    private void resumeKafka() {
        kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        paused = false;
    }
}
