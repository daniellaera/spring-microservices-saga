package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class OrderEventListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<ReserveStockCommand> commands = new ArrayList<>();
    private final OrderEventListener listener = new OrderEventListener(command -> {
        commands.add(command);
        return Optional.empty();
    }, objectMapper);

    @Test
    void multiItemOrder_mapsEveryFieldAndItem() {
        listener.consumeOrder("{\"orderId\":4,\"productName\":\"MacBook Pro\",\"quantity\":2,\"price\":1299.99,"
                + "\"totalAmount\":3349.95,\"userEmail\":\"user@test.com\",\"paymentIntentId\":\"pi_multi\",\"items\":["
                + "{\"productName\":\"MacBook Pro\",\"quantity\":2,\"price\":1299.99,\"totalAmount\":2599.98},"
                + "{\"productName\":\"AirPods\",\"quantity\":3,\"price\":249.99,\"totalAmount\":749.97}]}");

        assertThat(commands).containsExactly(new ReserveStockCommand(4L, "MacBook Pro", 2, new BigDecimal("1299.99"),
                new BigDecimal("3349.95"), "user@test.com", "pi_multi", List.of(
                        new OrderLine("MacBook Pro", 2, new BigDecimal("1299.99"), new BigDecimal("2599.98")),
                        new OrderLine("AirPods", 3, new BigDecimal("249.99"), new BigDecimal("749.97")))));
    }

    @Test
    void legacyOrderWithoutItems_resolvesOneLineFromTheLegacyFields() {
        listener.consumeOrder("{\"orderId\":1,\"productName\":\"MacBook Pro\",\"quantity\":2,\"price\":1299.99,"
                + "\"totalAmount\":2599.98,\"userEmail\":\"user@test.com\"}");

        assertThat(commands).singleElement().satisfies(command -> assertThat(command.lines()).containsExactly(
                new OrderLine("MacBook Pro", 2, new BigDecimal("1299.99"), new BigDecimal("2599.98"))));
    }

    @Test
    void emptyItems_alsoFallBackToTheLegacyFields() {
        listener.consumeOrder("{\"orderId\":1,\"productName\":\"MacBook Pro\",\"quantity\":2,\"price\":1299.99,"
                + "\"totalAmount\":2599.98,\"items\":[]}");

        assertThat(commands).singleElement()
                .satisfies(command -> assertThat(command.lines()).extracting(OrderLine::productName)
                        .containsExactly("MacBook Pro"));
    }

    @Test
    void malformedJson_propagatesToTheContainer() {
        // straight to the dead-letter topic: JacksonException is excluded from the retries
        assertThatThrownBy(() -> listener.consumeOrder("not json")).isInstanceOf(JacksonException.class);

        assertThat(commands).isEmpty();
    }

    @Test
    void useCaseFailure_propagatesToTheContainer() {
        OrderEventListener failing = new OrderEventListener(command -> {
            throw new IllegalStateException("database unavailable");
        }, objectMapper);

        assertThatThrownBy(() -> failing.consumeOrder("{\"orderId\":3,\"productName\":\"MacBook Pro\",\"quantity\":1}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    @Test
    void deadLetteredOrder_isLoggedWithTheRetryTopicsError(CapturedOutput output) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders-topic-inventory-dlt", 0, 0L, null, "{\"orderId\":7}");
        record.headers().add(KafkaHeaders.EXCEPTION_MESSAGE, "database unavailable".getBytes(StandardCharsets.UTF_8));

        listener.handleDlt(record);

        assertThat(output.getAll()).contains("DLT received failed message: {\"orderId\":7} error: database unavailable");
        assertThat(commands).isEmpty();
    }

    @Test
    void deadLetteredByAPlainRecoverer_readsItsDltExceptionHeader(CapturedOutput output) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders-topic-inventory-dlt", 0, 0L, null, "{\"orderId\":9}");
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "db down".getBytes(StandardCharsets.UTF_8));

        listener.handleDlt(record);

        assertThat(output.getAll()).contains("DLT received failed message: {\"orderId\":9} error: db down");
    }

    @Test
    void deadLetteredOrderWithoutTheExceptionHeader_neverThrows() {
        assertThatCode(() -> listener.handleDlt(new ConsumerRecord<>("orders-topic-inventory-dlt", 0, 0L, null, "not json")))
                .doesNotThrowAnyException();
    }

    @Test
    void retries_areNonBlockingRetryTopicsOfThisService_withTheProductionBudget() throws Exception {
        RetryableTopic retry = OrderEventListener.class.getMethod("consumeOrder", String.class)
                .getAnnotation(RetryableTopic.class);

        // retried after 2, 6, 18 and 54 s
        assertThat(retry.attempts()).isEqualTo("${inventory.retry.attempts:5}");
        assertThat(retry.backOff().delayString()).isEqualTo("${inventory.retry.delay:2000}");
        assertThat(retry.backOff().multiplierString()).isEqualTo("${inventory.retry.multiplier:3.0}");
        assertThat(retry.backOff().maxDelayString()).isEqualTo("${inventory.retry.max-delay:60000}");
        // orders-topic-inventory-retry-0, -1, ... and orders-topic-inventory-dlt
        assertThat(retry.retryTopicSuffix()).isEqualTo("-inventory-retry");
        assertThat(retry.dltTopicSuffix()).isEqualTo("-inventory-dlt");
        assertThat(retry.topicSuffixingStrategy()).isEqualTo(TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE);
        assertThat(retry.exclude()).containsExactly(JacksonException.class);
    }
}
