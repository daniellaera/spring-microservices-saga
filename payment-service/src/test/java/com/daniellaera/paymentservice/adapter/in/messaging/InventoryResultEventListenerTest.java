package com.daniellaera.paymentservice.adapter.in.messaging;

import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryResultEventListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<InventoryResult> results = new ArrayList<>();
    private final List<DeadLettered> deadLettered = new ArrayList<>();
    private final InventoryResultEventListener listener = new InventoryResultEventListener(results::add,
            (orderId, userEmail, error) -> deadLettered.add(new DeadLettered(orderId, userEmail, error)), objectMapper);

    private record DeadLettered(Long orderId, String userEmail, String error) {}

    @Test
    void inventoryResult_mapsEveryFieldAndItem() throws Exception {
        listener.handleInventoryResult("{\"orderId\":5,\"status\":\"APPROVED\",\"productName\":\"MacBook Pro\","
                + "\"quantity\":1,\"price\":1299.99,\"totalAmount\":1299.99,\"userEmail\":\"user@test.com\","
                + "\"paymentIntentId\":\"pi_999\",\"items\":[{\"productName\":\"MacBook Pro\",\"quantity\":1,"
                + "\"price\":1299.99,\"totalAmount\":1299.99}]}");

        assertThat(results).containsExactly(new InventoryResult(5L, "APPROVED", "MacBook Pro", 1,
                new BigDecimal("1299.99"), new BigDecimal("1299.99"), "user@test.com", "pi_999", List.of(
                        new InventoryResult.Item("MacBook Pro", 1, new BigDecimal("1299.99"),
                                new BigDecimal("1299.99")))));
    }

    @Test
    void withoutItems_keepsThemNull() throws Exception {
        listener.handleInventoryResult("{\"orderId\":4,\"status\":\"REJECTED\"}");

        assertThat(results).singleElement().satisfies(result -> assertThat(result.items()).isNull());
    }

    @Test
    void malformedJson_propagatesToTheContainer() {
        assertThatThrownBy(() -> listener.handleInventoryResult("not json")).isInstanceOf(JacksonException.class);
        assertThat(results).isEmpty();
    }

    @Test
    void useCaseFailure_propagatesToTheContainer() {
        InventoryResultEventListener failing = new InventoryResultEventListener(result -> {
            throw new IllegalStateException("db down");
        }, (orderId, userEmail, error) -> { }, objectMapper);

        assertThatThrownBy(() -> failing.handleInventoryResult("{\"orderId\":4,\"status\":\"REJECTED\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }

    @Test
    void deadLetteredResult_isReportedWithItsOrderAndTheError() {
        listener.handleDlt(dltRecord("{\"orderId\":7,\"status\":\"APPROVED\",\"userEmail\":\"user@test.com\"}",
                "Stripe unavailable: 503"));

        assertThat(deadLettered).containsExactly(new DeadLettered(7L, "user@test.com", "Stripe unavailable: 503"));
    }

    @Test
    void unreadableDeadLetteredResult_isStillReported_andNeverThrows() {
        assertThatCode(() -> listener.handleDlt(dltRecord("not json", "Unrecognized token"))).doesNotThrowAnyException();

        assertThat(deadLettered).containsExactly(new DeadLettered(null, null, "Unrecognized token"));
    }

    @Test
    void deadLetteredByAPlainRecoverer_readsItsDltExceptionHeader() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("inventory-topic-dlt", 0, 0L, null, "{\"orderId\":9}");
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "db down".getBytes(StandardCharsets.UTF_8));

        listener.handleDlt(record);

        assertThat(deadLettered).containsExactly(new DeadLettered(9L, null, "db down"));
    }

    @Test
    void deadLetteredResultWithoutTheExceptionHeader_isReportedWithoutError() {
        listener.handleDlt(new ConsumerRecord<>("inventory-topic-dlt", 0, 0L, null, "{\"orderId\":8}"));

        assertThat(deadLettered).containsExactly(new DeadLettered(8L, null, null));
    }

    private static ConsumerRecord<String, String> dltRecord(String value, String error) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("inventory-topic-dlt", 0, 0L, null, value);
        record.headers().add(KafkaHeaders.EXCEPTION_MESSAGE, error.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void retries_areNonBlockingRetryTopics_withTheProductionBudget() throws Exception {
        RetryableTopic retry = InventoryResultEventListener.class.getMethod("handleInventoryResult", String.class)
                .getAnnotation(RetryableTopic.class);

        assertThat(retry.attempts()).isEqualTo("${payment.retry.attempts:3}");
        assertThat(retry.backOff().delayString()).isEqualTo("${payment.retry.delay:1000}");
        assertThat(retry.backOff().multiplierString()).isEqualTo("${payment.retry.multiplier:2.0}");
        assertThat(retry.backOff().maxDelayString()).isEqualTo("${payment.retry.max-delay:10000}");
        // keeps the existing topic names inventory-topic-retry-0, -1, ...
        assertThat(retry.topicSuffixingStrategy()).isEqualTo(TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE);
    }
}
