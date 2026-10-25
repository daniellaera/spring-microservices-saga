package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.TestcontainersConfiguration;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEvent;
import com.daniellaera.orderservice.support.TopicReader;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        List<Long> delivered = ordersTopicOrderIds();
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
        assertThat(ordersTopicOrderIds()).contains(9102L);

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

    // the rows are PUBLISHED once the broker acked them: they are on the topic now
    private List<Long> ordersTopicOrderIds() {
        return TopicReader.readAll(kafka.getBootstrapServers(), "orders-topic").stream()
                .map(value -> objectMapper.readValue(value, OrderEvent.class).orderId())
                .toList();
    }

    private Map<?, ?> auditMessage(String eventType, String entityId) {
        return await().atMost(Duration.ofSeconds(30)).until(
                () -> TopicReader.readAll(kafka.getBootstrapServers(), "audit-topic").stream()
                        .map(value -> (Map<?, ?>) objectMapper.readValue(value, Map.class))
                        .filter(message -> eventType.equals(message.get("eventType"))
                                && entityId.equals(message.get("entityId")))
                        .findFirst(),
                Optional::isPresent).orElseThrow();
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
