package com.daniellaera.orderservice.adapter.in.messaging;

import com.daniellaera.orderservice.domain.port.in.ApplyPaymentResultUseCase;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class PaymentEventListenerTest {

    private final ApplyPaymentResultUseCase useCase = mock(ApplyPaymentResultUseCase.class);
    private final PaymentEventListener listener = new PaymentEventListener(useCase, JsonMapper.builder().build());

    @Test
    void mapsEveryFieldToThePaymentResult() {
        listener.handlePaymentEvent("""
                {"orderId":42,"productName":"MacBook Pro","quantity":1,"status":"SUCCESS","price":1299.99,
                 "totalAmount":1399.79,"userEmail":"user@test.com",
                 "items":[{"productName":"MacBook Pro","quantity":1,"price":1299.99,"totalAmount":1299.99}]}""");

        verify(useCase).applyPaymentResult(new PaymentResult(42L, "MacBook Pro", 1, "SUCCESS",
                new BigDecimal("1299.99"), new BigDecimal("1399.79"), "user@test.com"));
    }

    @Test
    void malformedJson_propagatesToTheContainer() {
        // straight to the dead-letter topic: JacksonException is excluded from the retries
        assertThatThrownBy(() -> listener.handlePaymentEvent("{not json")).isInstanceOf(JacksonException.class);

        verifyNoInteractions(useCase);
    }

    @Test
    void useCaseFailure_propagatesToTheContainer() {
        doThrow(new IllegalStateException("db down")).when(useCase).applyPaymentResult(any());

        assertThatThrownBy(() -> listener.handlePaymentEvent("{\"orderId\":1,\"status\":\"FAILED\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }

    @Test
    void deadLetteredPaymentResult_isOnlyLogged_withTheRetryTopicsError(CapturedOutput output) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment-topic-order-dlt", 0, 0L, null, "{\"orderId\":1}");
        record.headers().add(KafkaHeaders.EXCEPTION_MESSAGE, "db down".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> listener.handleDlt(record)).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("DLT received failed message: {\"orderId\":1} error: db down");
        verifyNoInteractions(useCase);
    }

    @Test
    void deadLetteredByAPlainRecoverer_readsItsDltExceptionHeader(CapturedOutput output) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment-topic-order-dlt", 0, 0L, null, "{\"orderId\":2}");
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "poison".getBytes(StandardCharsets.UTF_8));

        listener.handleDlt(record);

        assertThat(output.getAll()).contains("DLT received failed message: {\"orderId\":2} error: poison");
    }

    @Test
    void deadLetteredResultWithoutTheExceptionHeader_neverThrows() {
        assertThatCode(() -> listener.handleDlt(new ConsumerRecord<>("payment-topic-order-dlt", 0, 0L, null, "{}")))
                .doesNotThrowAnyException();
    }

    @Test
    void retries_areNonBlockingRetryTopicsOfThisService_withTheProductionBudget() throws Exception {
        RetryableTopic retry = PaymentEventListener.class.getMethod("handlePaymentEvent", String.class)
                .getAnnotation(RetryableTopic.class);

        // retried after 2, 6, 18 and 54 s
        assertThat(retry.attempts()).isEqualTo("${order.retry.attempts:5}");
        assertThat(retry.backOff().delayString()).isEqualTo("${order.retry.delay:2000}");
        assertThat(retry.backOff().multiplierString()).isEqualTo("${order.retry.multiplier:3.0}");
        assertThat(retry.backOff().maxDelayString()).isEqualTo("${order.retry.max-delay:60000}");
        // not inventory-service's payment-topic-inventory-retry-N: each service retries only its own failures
        assertThat(retry.retryTopicSuffix()).isEqualTo("-order-retry");
        assertThat(retry.dltTopicSuffix()).isEqualTo("-order-dlt");
        assertThat(retry.topicSuffixingStrategy()).isEqualTo(TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE);
        assertThat(retry.exclude()).containsExactly(JacksonException.class);
    }
}
