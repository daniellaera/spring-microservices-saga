package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.port.in.PaymentOutcome;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class PaymentEventListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<PaymentOutcome> outcomes = new ArrayList<>();
    private final PaymentEventListener listener = new PaymentEventListener(outcomes::add, objectMapper);

    @Test
    void paymentEvent_isMappedToAnOutcome() throws Exception {
        listener.handlePaymentResult("{\"orderId\":6,\"productName\":\"MacBook Pro\",\"quantity\":2,\"status\":\"FAILED\","
                + "\"price\":1299.99,\"totalAmount\":3349.95,\"userEmail\":\"user@test.com\",\"items\":[]}");

        assertThat(outcomes).containsExactly(new PaymentOutcome(6L, "FAILED", "user@test.com"));
    }

    @Test
    void malformedJson_propagatesToTheContainer() {
        assertThatThrownBy(() -> listener.handlePaymentResult("not json")).isInstanceOf(JacksonException.class);
        assertThat(outcomes).isEmpty();
    }

    @Test
    void useCaseFailure_propagatesToTheContainer() {
        PaymentEventListener failing = new PaymentEventListener(outcome -> {
            throw new IllegalStateException("db down");
        }, objectMapper);

        assertThatThrownBy(() -> failing.handlePaymentResult("{\"orderId\":6,\"status\":\"FAILED\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }

    @Test
    void deadLetteredPaymentResult_isLoggedWithTheRetryTopicsError(CapturedOutput output) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment-topic-inventory-dlt", 0, 0L, null, "{\"orderId\":6}");
        record.headers().add(KafkaHeaders.EXCEPTION_MESSAGE, "db down".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> listener.handleDlt(record)).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("DLT received failed message: {\"orderId\":6} error: db down");
        assertThat(outcomes).isEmpty();
    }

    @Test
    void retries_areNonBlockingRetryTopicsOfThisService_withTheProductionBudget() throws Exception {
        RetryableTopic retry = PaymentEventListener.class.getMethod("handlePaymentResult", String.class)
                .getAnnotation(RetryableTopic.class);

        assertThat(retry.attempts()).isEqualTo("${inventory.retry.attempts:5}");
        assertThat(retry.backOff().delayString()).isEqualTo("${inventory.retry.delay:2000}");
        assertThat(retry.backOff().multiplierString()).isEqualTo("${inventory.retry.multiplier:3.0}");
        assertThat(retry.backOff().maxDelayString()).isEqualTo("${inventory.retry.max-delay:60000}");
        // not order-service's payment-topic-order-retry-N: each service retries only its own failures
        assertThat(retry.retryTopicSuffix()).isEqualTo("-inventory-retry");
        assertThat(retry.dltTopicSuffix()).isEqualTo("-inventory-dlt");
        assertThat(retry.topicSuffixingStrategy()).isEqualTo(TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE);
        assertThat(retry.exclude()).containsExactly(JacksonException.class);
    }
}
