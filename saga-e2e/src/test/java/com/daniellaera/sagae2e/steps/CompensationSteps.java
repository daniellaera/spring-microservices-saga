package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.KafkaTraffic;
import com.daniellaera.sagae2e.support.KafkaTraffic.Message;
import com.daniellaera.sagae2e.support.SagaEnvironment;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daniellaera.sagae2e.support.KafkaTraffic.AUDIT_TOPIC;
import static com.daniellaera.sagae2e.support.KafkaTraffic.PAYMENT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Stock compensation, observed through payment-topic, inventory's consumer offsets and the audit trail. */
public class CompensationSteps {

    private static final String INVENTORY_COMPENSATION_GROUP = "inventory-compensation-group";
    // The audit events are sent asynchronously: how long to keep checking that no (further) one shows up
    private static final Duration AUDIT_GRACE = Duration.ofSeconds(3);

    private final ScenarioContext context;
    private final KafkaTraffic kafka = SagaEnvironment.get().kafka();

    public CompensationSteps(ScenarioContext context) {
        this.context = context;
    }

    @When("the payment failure of the order is delivered again")
    public void thePaymentFailureIsDeliveredAgain() {
        Message duplicate = kafka.publish(PAYMENT_TOPIC, paymentFailureOfTheOrder().value());
        awaitConsumedByInventory(duplicate);
    }

    @Then("no stock is restored for {string}")
    public void noStockIsRestored(String product) {
        awaitConsumedByInventory(paymentFailureOfTheOrder());
        long productId = context.product(product).id();
        await().during(AUDIT_GRACE).atMost(AUDIT_GRACE.plusSeconds(10))
                .untilAsserted(() -> assertThat(stockRestoredEvents(productId)).isEmpty());
    }

    @Then("stock is restored exactly once for {string}")
    public void stockIsRestoredExactlyOnce(String product) {
        long productId = context.product(product).id();
        await().untilAsserted(() -> assertThat(stockRestoredEvents(productId)).isNotEmpty());
        await().during(AUDIT_GRACE).atMost(AUDIT_GRACE.plusSeconds(10))
                .untilAsserted(() -> assertThat(stockRestoredEvents(productId)).hasSize(1));
    }

    private Message paymentFailureOfTheOrder() {
        long orderId = context.orderId();
        AtomicReference<Message> failure = new AtomicReference<>();
        await().untilAsserted(() -> {
            List<Message> failures = kafka.messages(PAYMENT_TOPIC).stream()
                    .filter(message -> message.json().path("orderId").asLong() == orderId)
                    .filter(message -> message.json().path("status").asString().equals("FAILED"))
                    .toList();
            assertThat(failures).isNotEmpty();
            failure.set(failures.get(0));
        });
        return failure.get();
    }

    // inventory commits its offset once a record is handled (ack-mode record)
    private void awaitConsumedByInventory(Message message) {
        await().until(() -> message.consumedBy(kafka.committedOffsets(INVENTORY_COMPENSATION_GROUP)));
    }

    private List<Message> stockRestoredEvents(long productId) {
        return kafka.messages(AUDIT_TOPIC).stream()
                .filter(message -> message.json().path("eventType").asString().equals("STOCK_RESTORED"))
                .filter(message -> message.json().path("entityId").asString().equals(String.valueOf(productId)))
                .toList();
    }
}
