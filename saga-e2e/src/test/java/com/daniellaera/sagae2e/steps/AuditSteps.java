package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.KafkaTraffic;
import com.daniellaera.sagae2e.support.KafkaTraffic.Message;
import com.daniellaera.sagae2e.support.SagaEnvironment;
import com.daniellaera.sagae2e.support.ShopApi;
import io.cucumber.java.en.Then;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.daniellaera.sagae2e.support.KafkaTraffic.AUDIT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AuditSteps {

    // The audit events are sent asynchronously: how long to keep checking that no further one shows up
    private static final Duration AUDIT_GRACE = Duration.ofSeconds(3);

    private final ScenarioContext context;
    private final KafkaTraffic kafka = SagaEnvironment.get().kafka();
    private final ShopApi shop = new ShopApi(SagaEnvironment.get());

    public AuditSteps(ScenarioContext context) {
        this.context = context;
    }

    /**
     * Every saga event carries the order id as its correlationId; order and payment events also as their entityId,
     * stock events have the product's. Audit events have no key: the trail is read in topic order (offset within
     * each partition).
     */
    @Then("the audit trail of the order shows, in order:")
    public void theAuditTrailOfTheOrderShows(List<Map<String, String>> expected) {
        String orderId = String.valueOf(context.orderId());
        Set<String> expectedEvents = Set.copyOf(expected.stream().map(row -> row.get("event")).toList());

        await().untilAsserted(() -> {
            List<Message> trail = kafka.messages(AUDIT_TOPIC).stream()
                    .filter(message -> expectedEvents.contains(message.json().path("eventType").asString()))
                    .filter(message -> message.json().path("correlationId").asString().equals(orderId))
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

    /**
     * The order's saga as audit-trail-service shows it (GET /audit/orders/{id}/timeline), oldest first. Rows with the
     * same step happen concurrently (order-service and inventory-service both react to the payment result) and may be
     * recorded in either order; the steps follow each other.
     */
    @Then("the saga timeline of the order shows:")
    public void theSagaTimelineOfTheOrderShows(List<Map<String, String>> expected) {
        long orderId = context.orderId();
        List<List<Map<String, String>>> steps = expected.stream()
                .collect(Collectors.groupingBy(row -> Integer.parseInt(row.get("step")), TreeMap::new,
                        Collectors.mapping(row -> Map.of("event", row.get("event"), "service", row.get("service")),
                                Collectors.toList())))
                .values().stream().toList();

        await().untilAsserted(() -> {
            List<JsonNode> timeline = shop.timeline(orderId);
            assertThat(timeline).hasSize(expected.size());
            int position = 0;
            for (List<Map<String, String>> step : steps) {
                List<Map<String, String>> recorded = timeline.subList(position, position + step.size()).stream()
                        .map(event -> Map.of("event", event.path("eventType").asString(),
                                "service", event.path("serviceName").asString()))
                        .toList();
                assertThat(recorded).containsExactlyInAnyOrderElementsOf(step);
                position += step.size();
            }
            assertThat(timeline).allSatisfy(event -> {
                assertThat(event.path("correlationId").asString()).isEqualTo(String.valueOf(orderId));
                // the instant, in UTC
                assertThat(event.path("recordedAt").asString()).endsWith("Z");
            });
            assertThat(timeline).extracting(event -> Instant.parse(event.path("recordedAt").asString()))
                    .isSorted();
        });
    }
}
