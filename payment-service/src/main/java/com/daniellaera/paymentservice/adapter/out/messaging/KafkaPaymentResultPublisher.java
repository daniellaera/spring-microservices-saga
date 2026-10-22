package com.daniellaera.paymentservice.adapter.out.messaging;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import com.daniellaera.paymentservice.domain.port.out.PaymentResultPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KafkaPaymentResultPublisher implements PaymentResultPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Unlike inventory-service's result publisher, nothing is caught here (current behaviour): a serialization or
    // send failure propagates to the listener, which catches nothing either, so the container's error handler
    // retries the record.
    @Override
    public void publish(InventoryResult result, PaymentStatus status) {
        kafkaTemplate.send("payment-topic", objectMapper.writeValueAsString(toEvent(result, status)));
    }

    // items are passed through untouched, null included
    static PaymentEvent toEvent(InventoryResult result, PaymentStatus status) {
        List<OrderItemEvent> items = result.items() == null ? null : result.items().stream()
                .map(item -> new OrderItemEvent(item.productName(), item.quantity(), item.price(), item.totalAmount()))
                .toList();
        return new PaymentEvent(result.orderId(), result.productName(), result.quantity(), status.name(),
                result.price(), result.totalAmount(), result.userEmail(), items);
    }
}
