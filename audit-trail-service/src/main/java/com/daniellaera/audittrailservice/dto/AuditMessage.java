package com.daniellaera.audittrailservice.dto;

public record AuditMessage(
        String eventType,     // AuditEventType name
        String userEmail,
        String entityType,
        String entityId,
        String payload,       // JSON string
        String serviceName,
        String timestamp      // ISO-8601
) {
}
