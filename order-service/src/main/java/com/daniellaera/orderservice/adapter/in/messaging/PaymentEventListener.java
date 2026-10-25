package com.daniellaera.orderservice.adapter.in.messaging;

import com.daniellaera.orderservice.domain.port.in.ApplyPaymentResultUseCase;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import com.daniellaera.orderservice.domain.port.in.ReportDeadLetteredResultUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final ApplyPaymentResultUseCase applyPaymentResultUseCase;
    private final ReportDeadLetteredResultUseCase reportDeadLetteredResultUseCase;
    private final ObjectMapper objectMapper;

    // Nothing is caught: a failing payment result is retried on the retry topics without holding back payment-topic
    // (non-blocking retries), then dead-lettered to handleDlt; an unreadable one goes there at once. Declared here, as
    // in payment-service, rather than with spring.kafka.retry.topic.*, which ignores this class's @DltHandler and uses
    // Spring Kafka's default one: that one fails on every record under ack-mode: record and loops forever.
    // inventory-service also consumes payment-topic with retry topics: the suffixes keep the two services' topics
    // apart (payment-topic-order-retry-N, payment-topic-order-dlt), or each would re-process the other's failures.
    // Budget: 5 attempts, retried after 2, 6, 18 and 54 s (~80 s).
    @RetryableTopic(
            attempts = "${order.retry.attempts:5}",
            backOff = @BackOff(delayString = "${order.retry.delay:2000}",
                    multiplierString = "${order.retry.multiplier:3.0}",
                    maxDelayString = "${order.retry.max-delay:60000}"),
            retryTopicSuffix = "-order-retry",
            dltTopicSuffix = "-order-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            exclude = JacksonException.class)
    @KafkaListener(topics = "payment-topic", groupId = "order-group")
    public void handlePaymentEvent(String message) {
        PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
        applyPaymentResultUseCase.applyPaymentResult(new PaymentResult(event.orderId(), event.productName(),
                event.quantity(), event.status(), event.price(), event.totalAmount(), event.userEmail()));
    }

    // Called once per dead-lettered payment result: the order stays PENDING. Logged and audited (ORDER_DEAD_LETTERED).
    // It must not throw: the DLT container would retry it forever.
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        // the retry topics record it as kafka_exception-message, a plain dead-letter recoverer as kafka_dlt-...
        String error = headerValue(record, KafkaHeaders.EXCEPTION_MESSAGE);
        if (error == null) {
            error = headerValue(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        }
        log.error("DLT received failed message: {} error: {}", record.value(), error);
        PaymentEvent event;
        try {
            event = objectMapper.readValue(record.value(), PaymentEvent.class);
        } catch (Exception e) {
            reportDeadLetteredResultUseCase.deadLettered(null, null, error);
            return;
        }
        reportDeadLetteredResultUseCase.deadLettered(event.orderId(), event.userEmail(), error);
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
