package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.KafkaTraffic;
import com.daniellaera.sagae2e.support.KafkaTraffic.Message;
import com.daniellaera.sagae2e.support.SagaEnvironment;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daniellaera.sagae2e.support.KafkaTraffic.INVENTORY_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Redelivers inventory's verdict on the order to payment-service, as Kafka would after a consumer crash. */
public class RedeliverySteps {

    private static final String PAYMENT_GROUP = "payment-group";

    private final ScenarioContext context;
    private final KafkaTraffic kafka = SagaEnvironment.get().kafka();

    public RedeliverySteps(ScenarioContext context) {
        this.context = context;
    }

    @When("the inventory result of the order is delivered again")
    public void theInventoryResultIsDeliveredAgain() {
        long orderId = context.orderId();
        AtomicReference<Message> original = new AtomicReference<>();
        await().untilAsserted(() -> {
            List<Message> results = kafka.messages(INVENTORY_TOPIC).stream()
                    .filter(message -> message.json().path("orderId").asLong() == orderId)
                    .toList();
            assertThat(results).hasSize(1);
            original.set(results.get(0));
        });

        Message duplicate = kafka.publish(INVENTORY_TOPIC, original.get().value());

        // payment-service commits its offset once a record is handled (ack-mode record)
        await().until(() -> duplicate.consumedBy(kafka.committedOffsets(PAYMENT_GROUP)));
    }
}
