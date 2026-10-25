package com.daniellaera.audittrailservice.domain.port.in;

/** Stores one audit event. Never throws: an audit failure must not break the caller. */
public interface RecordAuditEventUseCase {

    void record(RecordAuditEventCommand command);
}
