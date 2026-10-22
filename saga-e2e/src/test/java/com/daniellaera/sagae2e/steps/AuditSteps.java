package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.KafkaTraffic;
import com.daniellaera.sagae2e.support.KafkaTraffic.Message;
import com.daniellaera.sagae2e.support.SagaEnvironment;
import io.cucumber.java.en.Then;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.daniellaera.sagae2e.support.KafkaTraffic.AUDIT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AuditSteps {

    // The audit events are sent asynchronously: how long to keep checking that no further one shows up
    private static final Duration AUDIT_GRACE = Duration.ofSeconds(3);

    private final ScenarioContext context;
    private final KafkaTraffic kafka = SagaEnvironment.get().kafka();

    public AuditSteps(ScenarioContext context) {
        this.context = context;
    }

    /**
     * Order and payment events carry the order id as entityId. Stock events carry the product id instead (no
     * correlation id yet, see the backlog), so they are matched on the scenario's products, which no other order
     * touches. Audit events have no key: the trail is read in topic order (offset within each partition).
     */
    @Then("the audit trail of the order shows, in order:")
    public void theAuditTrailOfTheOrderShows(List<Map<String, String>> expected) {
        String orderId = String.valueOf(context.orderId());
        Set<String> expectedEvents = Set.copyOf(expected.stream().map(row -> row.get("event")).toList());

        await().untilAsserted(() -> {
            List<Message> trail = kafka.messages(AUDIT_TOPIC).stream()
                    .filter(message -> expectedEvents.contains(message.json().path("eventType").asString()))
                    .filter(message -> concernsTheOrder(message, orderId))
                    .sorted(Comparator.comparingInt(Message::partition).thenComparingLong(Message::offset))
                    .toList();

            assertThat(trail).extracting(message -> Map.of(
                            "event", message.json().path("eventType").asString(),
                            "service", message.json().path("serviceName").asString()))
                    .containsExactlyElementsOf(expected);
            assertThat(trail)
                    .filteredOn(message -> !message.json().path("entityType").asString().equals("PRODUCT"))
                    .allSatisfy(message -> assertThat(message.json().path("entityId").asString()).isEqualTo(orderId));
        });
        assertThat(kafka.messages(AUDIT_TOPIC)).extracting(Message::partition).containsOnly(0);
    }

    @Then("{word} is audited exactly once for the order")
    public void isAuditedExactlyOnceForTheOrder(String eventType) {
        String orderId = String.valueOf(context.orderId());
        await().untilAsserted(() -> assertThat(auditEvents(eventType, orderId)).isNotEmpty());
        await().during(AUDIT_GRACE).atMost(AUDIT_GRACE.plusSeconds(10))
                .untilAsserted(() -> assertThat(auditEvents(eventType, orderId)).hasSize(1));
    }

    private List<Message> auditEvents(String eventType, String entityId) {
        return kafka.messages(AUDIT_TOPIC).stream()
                .filter(message -> message.json().path("eventType").asString().equals(eventType))
                .filter(message -> message.json().path("entityId").asString().equals(entityId))
                .toList();
    }

    private boolean concernsTheOrder(Message message, String orderId) {
        String entityId = message.json().path("entityId").asString();
        if (message.json().path("entityType").asString().equals("PRODUCT")) {
            return context.productIds().contains(Long.parseLong(entityId));
        }
        return entityId.equals(orderId);
    }
}
