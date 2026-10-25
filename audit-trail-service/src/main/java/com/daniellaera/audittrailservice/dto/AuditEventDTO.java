package com.daniellaera.audittrailservice.dto;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * createdAt is the recording time in UTC without an offset, as it has always been sent; occurredAt is the same instant
 * with its offset (ISO-8601, Z), for clients that show it in their own time zone. correlationId is the order id of a
 * saga event, null for the others and for events recorded before it existed.
 */
public record AuditEventDTO(
        Long id,
        String eventType,
        String userEmail,
        String entityType,
        String entityId,
        String payload,
        String serviceName,
        LocalDateTime createdAt,
        String correlationId,
        Instant occurredAt
) {
}
