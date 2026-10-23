package com.daniellaera.paymentservice.adapter.out.messaging;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;
import com.daniellaera.paymentservice.domain.port.out.AuditTrail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaAuditTrail implements AuditTrail {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async
    @Override
    public void paymentInitiated(String paymentIntentId, CreatePaymentIntentCommand command) {
        publish("PAYMENT_INITIATED", command.userEmail(), paymentIntentId,
                () -> PaymentIntentRequestAuditPayload.of(command));
    }

    @Async
    @Override
    public void paymentProcessed(PaymentTransaction transaction, String userEmail) {
        publish(transaction.succeeded() ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED", userEmail,
                String.valueOf(transaction.orderId()), () -> TransactionAuditPayload.of(transaction));
    }

    @Async
    @Override
    public void paymentRefunded(PaymentTransaction transaction, String userEmail) {
        publish("PAYMENT_REFUNDED", userEmail, String.valueOf(transaction.orderId()),
                () -> new RefundAuditPayload(transaction.orderId(), transaction.paymentIntentId(),
                        transaction.refundId()));
    }

    @Async
    @Override
    public void paymentRefundFailed(PaymentTransaction transaction, String errorCode, String userEmail) {
        publish("PAYMENT_REFUND_FAILED", userEmail, String.valueOf(transaction.orderId()),
                () -> new RefundFailedAuditPayload(transaction.orderId(), transaction.paymentIntentId(), errorCode));
    }

    @Async
    @Override
    public void paymentDeadLettered(Long orderId, String userEmail, String error) {
        publish("PAYMENT_DEAD_LETTERED", userEmail, orderId != null ? String.valueOf(orderId) : null,
                () -> new DeadLetterAuditPayload(orderId, error));
    }

    private void publish(String eventType, String userEmail, String entityId, Supplier<Object> payload) {
        try {
            Map<String, Object> auditMessage = Map.of(
                    "eventType", eventType,
                    "userEmail", userEmail != null ? userEmail : "system",
                    "entityType", "PAYMENT",
                    "entityId", entityId != null ? entityId : "",
                    "payload", objectMapper.writeValueAsString(payload.get()),
                    "serviceName", "payment-service",
                    "timestamp", LocalDateTime.now().toString()
            );
            kafkaTemplate.send("audit-topic", objectMapper.writeValueAsString(auditMessage));
        } catch (Exception e) {
            log.warn("=== Audit: failed to publish event: {}", e.getMessage());
            // never throw
        }
    }
}
