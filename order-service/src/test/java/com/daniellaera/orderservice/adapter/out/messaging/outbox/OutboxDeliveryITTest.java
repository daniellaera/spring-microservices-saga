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
 * nothing is marked published while Kafka is down, and once it is back every row goes out, in order.
 */
@SpringBootTest(properties = {
        // the prod producer timeouts (config-server order-service.yml), so a send fails within seconds
        "spring.kafka.producer.properties.max.block.ms=1000",
        "spring.kafka.producer.properties.request.timeout.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=2000",
        "outbox.relay.send-timeout=3s"
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

        // two relay ticks (every 5 s) with Kafka unreachable
        await().during(Duration.ofSeconds(12)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRepository.findAll()).noneMatch(OutboxEventJpaEntity::isPublished));

        resumeKafka();

        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(outboxRepository.findAllById(List.of(first, second)))
                        .hasSize(2).allMatch(OutboxEventJpaEntity::isPublished));
        // at least once, and the first row before the second
        List<Long> delivered = ordersTopicOrderIds();
        assertThat(delivered).contains(9001L, 9002L);
        assertThat(delivered.indexOf(9001L)).isLessThan(delivered.indexOf(9002L));
    }

    private UUID outboxRow(long orderId) {
        OutboxEventJpaEntity row = new OutboxEventJpaEntity();
        row.setAggregateId(orderId);
        row.setEventType("ORDER_CREATED");
        row.setPayload(objectMapper.writeValueAsString(new OrderEvent(orderId, "Item", 1, BigDecimal.TEN,
                BigDecimal.TEN, "user@test.com", "pi_" + orderId, List.of())));
        return outboxRepository.save(row).getId();
    }

    private List<Long> ordersTopicOrderIds() {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("orders-topic"));
            List<Long> orderIds = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    orderIds.add(objectMapper.readValue(record.value(), OrderEvent.class).orderId());
                }
                return orderIds.contains(9001L) && orderIds.contains(9002L);
            });
            return orderIds;
        }
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
