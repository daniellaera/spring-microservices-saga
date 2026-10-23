package com.daniellaera.paymentservice.adapter.in.messaging;

import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import com.daniellaera.paymentservice.domain.port.in.ProcessInventoryResultUseCase;
import com.daniellaera.paymentservice.domain.port.in.ReportDeadLetteredResultUseCase;
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
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryResultEventListener {

    private final ProcessInventoryResultUseCase processInventoryResultUseCase;
    private final ReportDeadLetteredResultUseCase reportDeadLetteredResultUseCase;
    private final ObjectMapper objectMapper;

    // Nothing is caught: a failure is retried on the retry topics without holding back inventory-topic (non-blocking
    // retries), then dead-lettered to handleDlt. Declared here rather than with spring.kafka.retry.topic.*, which
    // ignores this class's @DltHandler and uses Spring Kafka's default DLT handler, which needs a MANUAL ack mode
    // and so failed on every dead-lettered record. Index suffixes keep the topic names inventory-topic-retry-N.
    // Budget: 5 attempts, retried after 2, 6, 18 and 54 s (~80 s), so that a short Stripe outage does not leave the
    // order PENDING in the dead-letter topic.
    @RetryableTopic(
            attempts = "${payment.retry.attempts:5}",
            backOff = @BackOff(delayString = "${payment.retry.delay:2000}",
                    multiplierString = "${payment.retry.multiplier:3.0}",
                    maxDelayString = "${payment.retry.max-delay:60000}"),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "inventory-topic", groupId = "payment-group")
    public void handleInventoryResult(String message) throws Exception {
        InventoryResultEvent event = objectMapper.readValue(message, InventoryResultEvent.class);
        processInventoryResultUseCase.process(toResult(event));
    }

    // items are passed through untouched, null included
    static InventoryResult toResult(InventoryResultEvent event) {
        return new InventoryResult(event.orderId(), event.status(), event.productName(), event.quantity(),
                event.price(), event.totalAmount(), event.userEmail(), event.paymentIntentId(),
                event.items() == null ? null : event.items().stream()
                        .map(item -> new InventoryResult.Item(item.productName(), item.quantity(), item.price(),
                                item.totalAmount()))
                        .toList());
    }

    // Called once per dead-lettered record. It must not throw: the DLT container would retry it forever.
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        // the retry topics record it as kafka_exception-message, a plain dead-letter recoverer as kafka_dlt-...
        String error = headerValue(record, KafkaHeaders.EXCEPTION_MESSAGE);
        if (error == null) {
            error = headerValue(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        }
        log.error("DLT received failed message: {} error: {}", record.value(), error);
        InventoryResultEvent event;
        try {
            event = objectMapper.readValue(record.value(), InventoryResultEvent.class);
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
