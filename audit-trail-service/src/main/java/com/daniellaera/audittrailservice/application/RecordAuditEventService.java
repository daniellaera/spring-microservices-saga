package com.daniellaera.audittrailservice.application;

import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import com.daniellaera.audittrailservice.domain.port.in.RecordAuditEventCommand;
import com.daniellaera.audittrailservice.domain.port.in.RecordAuditEventUseCase;
import com.daniellaera.audittrailservice.domain.port.out.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class RecordAuditEventService implements RecordAuditEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordAuditEventService.class);

    private final AuditEventRepository repository;

    public RecordAuditEventService(AuditEventRepository repository) {
        this.repository = repository;
    }

    // An event type that is not an AuditEventType is logged and dropped, like any other failure
    @Override
    @Transactional
    public void record(RecordAuditEventCommand command) {
        try {
            repository.save(AuditEvent.of(
                    AuditEventType.valueOf(command.eventType()),
                    command.userEmail(),
                    command.entityType(),
                    command.entityId(),
                    command.payload(),
                    command.serviceName(),
                    command.correlationId()));
            log.info("=== Audit: recorded {} for {} entity={}",
                    command.eventType(),
                    command.userEmail(),
                    command.entityId());
        } catch (Exception e) {
            log.error("=== Audit: failed to record event: {}", e.getMessage());
            // never throw — audit failure must not break business flow
        }
    }
}
