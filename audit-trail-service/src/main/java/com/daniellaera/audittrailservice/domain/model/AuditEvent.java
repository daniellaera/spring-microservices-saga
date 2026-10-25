package com.daniellaera.audittrailservice.domain.model;

import java.time.LocalDateTime;

/**
 * One recorded business event. Append-only: an event is created once, from what a service sent to audit-topic, and
 * never changed. id and createdAt are assigned when it is stored; createdAt is UTC, without an offset.
 * correlationId is the order id of a saga event, null outside a saga and for events recorded before it existed.
 */
public record AuditEvent(
        Long id,
        AuditEventType eventType,
        String userEmail,
        String entityType,
        String entityId,
        String payload,
        String serviceName,
        String correlationId,
        LocalDateTime createdAt
) {

    // what a service sent, not yet stored
    public static AuditEvent of(AuditEventType eventType, String userEmail, String entityType, String entityId,
                                String payload, String serviceName, String correlationId) {
        return new AuditEvent(null, eventType, userEmail, entityType, entityId, payload, serviceName, correlationId,
                null);
    }
}
