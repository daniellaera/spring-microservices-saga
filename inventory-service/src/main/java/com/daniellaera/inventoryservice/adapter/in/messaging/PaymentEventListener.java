package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.port.in.CompensateStockUseCase;
import com.daniellaera.inventoryservice.domain.port.in.PaymentOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final CompensateStockUseCase compensateStockUseCase;
    private final ObjectMapper objectMapper;

    // Nothing is caught: a failing compensation is retried on the retry topics, then dead-lettered to handleDlt; an
    // unreadable event goes there at once (see OrderEventListener). order-service also consumes payment-topic with
    // retry topics: the suffixes keep the two services' topics apart (payment-topic-inventory-retry-N,
    // payment-topic-inventory-dlt), or each would re-process the other's failures.
    @RetryableTopic(
            attempts = "${inventory.retry.attempts:5}",
            backOff = @BackOff(delayString = "${inventory.retry.delay:2000}",
                    multiplierString = "${inventory.retry.multiplier:3.0}",
                    maxDelayString = "${inventory.retry.max-delay:60000}"),
            retryTopicSuffix = "-inventory-retry",
            dltTopicSuffix = "-inventory-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            exclude = JacksonException.class)
    @KafkaListener(topics = "payment-topic", groupId = "inventory-compensation-group")
    public void handlePaymentResult(String message) throws Exception {
        PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
        compensateStockUseCase.compensate(new PaymentOutcome(event.orderId(), event.status(), event.userEmail()));
    }

    // Called once per dead-lettered payment result, whose stock is then not restored. It must not throw: the DLT
    // container would retry it forever.
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        log.error("DLT received failed message: {} error: {}", record.value(), DeadLetters.errorOf(record));
    }
}
