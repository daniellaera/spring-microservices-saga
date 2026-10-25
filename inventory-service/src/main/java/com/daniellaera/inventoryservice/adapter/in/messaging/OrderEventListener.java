package com.daniellaera.inventoryservice.adapter.in.messaging;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.port.in.ReportDeadLetteredUseCase;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockUseCase;
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

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final ReserveStockUseCase reserveStockUseCase;
    private final ReportDeadLetteredUseCase reportDeadLetteredUseCase;
    private final ObjectMapper objectMapper;

    // Nothing is caught (an unknown product is no failure: the use case rejects the order). A failing order is retried
    // on the retry topics without holding back orders-topic (non-blocking retries), then dead-lettered to handleDlt;
    // an unreadable one goes there at once. Declared here, as in payment-service, rather than with
    // spring.kafka.retry.topic.*, which ignores this class's @DltHandler and uses Spring Kafka's default one: that one
    // fails on every record under ack-mode: record and loops forever. The suffixes name the topics after this service
    // (orders-topic-inventory-retry-N, orders-topic-inventory-dlt), so no other consumer of orders-topic can share them.
    // Budget: 5 attempts, retried after 2, 6, 18 and 54 s (~80 s).
    @RetryableTopic(
            attempts = "${inventory.retry.attempts:5}",
            backOff = @BackOff(delayString = "${inventory.retry.delay:2000}",
                    multiplierString = "${inventory.retry.multiplier:3.0}",
                    maxDelayString = "${inventory.retry.max-delay:60000}"),
            retryTopicSuffix = "-inventory-retry",
            dltTopicSuffix = "-inventory-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            exclude = JacksonException.class)
    @KafkaListener(topics = "orders-topic", groupId = "inventory-group")
    public void consumeOrder(String message) {
        OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
        reserveStockUseCase.reserve(toCommand(event));
    }

    static ReserveStockCommand toCommand(OrderEvent event) {
        List<OrderLine> lines = resolveItems(event.items(), event.productName(), event.quantity(),
                event.price(), event.totalAmount()).stream()
                .map(item -> new OrderLine(item.productName(), item.quantity(), item.price(), item.totalAmount()))
                .toList();
        return new ReserveStockCommand(event.orderId(), event.productName(), event.quantity(), event.price(),
                event.totalAmount(), event.userEmail(), event.paymentIntentId(), lines);
    }

    // Orders from before multi-item support carry only the legacy single-item fields
    private static List<OrderItemEvent> resolveItems(List<OrderItemEvent> items, String productName, Integer quantity,
                                                     BigDecimal price, BigDecimal totalAmount) {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        return List.of(new OrderItemEvent(productName, quantity, price, totalAmount));
    }

    // Called once per dead-lettered order, which stays PENDING (no stock reserved): logged and audited
    // (STOCK_RESERVATION_DEAD_LETTERED). It must not throw: the DLT container would retry it forever.
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        String error = DeadLetters.errorOf(record);
        log.error("DLT received failed message: {} error: {}", record.value(), error);
        OrderEvent event;
        try {
            event = objectMapper.readValue(record.value(), OrderEvent.class);
        } catch (Exception e) {
            reportDeadLetteredUseCase.reservationDeadLettered(null, null, error);
            return;
        }
        reportDeadLetteredUseCase.reservationDeadLettered(event.orderId(), event.userEmail(), error);
    }
}
