package com.daniellaera.audittrailservice.consumer;

import com.daniellaera.audittrailservice.TestcontainersConfiguration;
import com.daniellaera.audittrailservice.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditConsumerITTest {

    private static final String AUDIT_TOPIC = "audit-topic";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
    }

    @Test
    void consume_validMessage_savesToDb() {
        String json = """
                {
                  "eventType": "ORDER_CREATED",
                  "userEmail": "buyer@test.com",
                  "entityType": "ORDER",
                  "entityId": "42",
                  "payload": "{}",
                  "serviceName": "order-service",
                  "timestamp": "2026-01-01T00:00:00"
                }
                """;

        kafkaTemplate.send(AUDIT_TOPIC, json);

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(auditEventRepository.findByUserEmailOrderByCreatedAtDesc("buyer@test.com"))
                                .hasSize(1)
                );
    }

    @Test
    void consume_invalidJson_swallowedNoException() {
        kafkaTemplate.send(AUDIT_TOPIC, "not-valid-json");

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .during(3, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(auditEventRepository.findAll()).isEmpty()
                );
    }

    @Test
    void consume_unknownEventType_notSaved() {
        String json = """
                {
                  "eventType": "NOT_A_REAL_EVENT",
                  "userEmail": "buyer@test.com",
                  "entityType": "ORDER",
                  "entityId": "42",
                  "payload": "{}",
                  "serviceName": "order-service",
                  "timestamp": "2026-01-01T00:00:00"
                }
                """;

        kafkaTemplate.send(AUDIT_TOPIC, json);

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .during(3, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(auditEventRepository.findAll()).isEmpty()
                );
    }
}
