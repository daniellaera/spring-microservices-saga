package com.daniellaera.audittrailservice.consumer;

import com.daniellaera.audittrailservice.TestcontainersConfiguration;
import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import com.daniellaera.audittrailservice.adapter.out.persistence.AuditEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private AuditEventJpaRepository auditEventRepository;

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

    // payment compensation (PR #17) and outbox dead-letter events: before they were AuditEventTypes, all were dropped
    @ParameterizedTest
    @ValueSource(strings = {"PAYMENT_REFUNDED", "PAYMENT_REFUND_FAILED", "PAYMENT_DEAD_LETTERED", "OUTBOX_EVENT_FAILED"})
    void consume_compensationAndOutboxEvents_savedWithTheirType(String eventType) {
        String json = """
                {
                  "eventType": "%s",
                  "userEmail": "system",
                  "entityType": "PAYMENT",
                  "entityId": "77",
                  "payload": "{\\"orderId\\":77}",
                  "serviceName": "payment-service",
                  "timestamp": "2026-10-23T00:00:00"
                }
                """.formatted(eventType);

        kafkaTemplate.send(AUDIT_TOPIC, json);

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(auditEventRepository.findAll()).singleElement().satisfies(saved -> {
                    assertThat(saved.getEventType()).isEqualTo(AuditEventType.valueOf(eventType));
                    assertThat(saved.getEntityId()).isEqualTo("77");
                    assertThat(saved.getPayload()).isEqualTo("{\"orderId\":77}");
                }));
    }

    @Test
    void consume_sagaEvent_keepsItsCorrelationId() {
        kafkaTemplate.send(AUDIT_TOPIC, """
                {"eventType":"STOCK_DEDUCTED","userEmail":"buyer@test.com","entityType":"PRODUCT","entityId":"3",
                 "payload":"{}","serviceName":"inventory-service","timestamp":"2026-10-25T08:00:00",
                 "correlationId":"42"}
                """);

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(auditEventRepository.findAll()).singleElement().satisfies(saved -> {
                    assertThat(saved.getEntityId()).isEqualTo("3");
                    assertThat(saved.getCorrelationId()).isEqualTo("42");
                }));
    }

    // A producer deployed before this service may send fields it does not know yet: the event is still recorded
    @Test
    void consume_messageWithAFieldItDoesNotKnow_isStillRecorded() {
        kafkaTemplate.send(AUDIT_TOPIC, """
                {"eventType":"ORDER_CREATED","userEmail":"buyer@test.com","entityType":"ORDER","entityId":"43",
                 "payload":"{}","serviceName":"order-service","timestamp":"2026-10-25T08:00:00",
                 "correlationId":"43","traceParent":"00-abc-def-01"}
                """);

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(auditEventRepository.findAll()).singleElement()
                        .satisfies(saved -> assertThat(saved.getCorrelationId()).isEqualTo("43")));
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
