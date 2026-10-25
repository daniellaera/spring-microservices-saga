package com.daniellaera.audittrailservice.adapter.out.persistence;

import com.daniellaera.audittrailservice.domain.model.AuditEvent;

final class AuditEventJpaMapper {

    private AuditEventJpaMapper() {
    }

    // a new row: id and createdAt are assigned when it is persisted
    static AuditEventJpaEntity toNewEntity(AuditEvent event) {
        return AuditEventJpaEntity.of(event.eventType(), event.userEmail(), event.entityType(), event.entityId(),
                event.payload(), event.serviceName(), event.correlationId());
    }

    static AuditEvent toDomain(AuditEventJpaEntity entity) {
        return new AuditEvent(entity.getId(), entity.getEventType(), entity.getUserEmail(), entity.getEntityType(),
                entity.getEntityId(), entity.getPayload(), entity.getServiceName(), entity.getCorrelationId(),
                entity.getCreatedAt());
    }
}
