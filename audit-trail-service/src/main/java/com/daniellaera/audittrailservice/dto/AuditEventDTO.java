package com.daniellaera.audittrailservice.dto;

import java.time.LocalDateTime;

public record AuditEventDTO(
        Long id,
        String eventType,
        String userEmail,
        String entityType,
        String entityId,
        String payload,
        String serviceName,
        LocalDateTime createdAt
) {
}
