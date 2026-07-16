package com.daniellaera.audittrailservice.consumer;

import com.daniellaera.audittrailservice.dto.AuditMessage;
import com.daniellaera.audittrailservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditConsumer {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "audit-topic", groupId = "audit-group")
    public void consume(String message) {
        try {
            AuditMessage auditMessage = objectMapper.readValue(message, AuditMessage.class);
            auditService.record(auditMessage);
        } catch (Exception e) {
            log.error("=== Audit: failed to parse message: {}", e.getMessage());
        }
    }
}
