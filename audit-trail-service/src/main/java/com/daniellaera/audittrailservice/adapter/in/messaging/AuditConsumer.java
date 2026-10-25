package com.daniellaera.audittrailservice.adapter.in.messaging;

import com.daniellaera.audittrailservice.domain.port.in.RecordAuditEventCommand;
import com.daniellaera.audittrailservice.domain.port.in.RecordAuditEventUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditConsumer {

    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "audit-topic", groupId = "audit-group")
    public void consume(String message) {
        try {
            AuditMessage auditMessage = objectMapper.readValue(message, AuditMessage.class);
            recordAuditEventUseCase.record(toCommand(auditMessage));
        } catch (Exception e) {
            log.error("=== Audit: failed to parse message: {}", e.getMessage());
        }
    }

    // the producer's timestamp is not kept: the event is stamped when it is stored
    static RecordAuditEventCommand toCommand(AuditMessage message) {
        return new RecordAuditEventCommand(message.eventType(), message.userEmail(), message.entityType(),
                message.entityId(), message.payload(), message.serviceName(), message.correlationId());
    }
}
