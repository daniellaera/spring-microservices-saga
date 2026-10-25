package com.daniellaera.audittrailservice.domain.port.in;

/**
 * What a service sent to audit-topic. eventType is the raw name: one that is not an AuditEventType is dropped by the
 * use case. correlationId is absent (null) in older messages and outside a saga.
 */
public record RecordAuditEventCommand(
        String eventType,
        String userEmail,
        String entityType,
        String entityId,
        String payload,
        String serviceName,
        String correlationId
) {
}
