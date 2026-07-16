package com.daniellaera.paymentservice.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async
    public void publish(String eventType,
                         String userEmail,
                         String entityType,
                         String entityId,
                         Object payload) {
        try {
            Map<String, Object> auditMessage = Map.of(
                    "eventType", eventType,
                    "userEmail", userEmail != null ? userEmail : "system",
                    "entityType", entityType,
                    "entityId", entityId != null ? entityId : "",
                    "payload", objectMapper.writeValueAsString(payload),
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
