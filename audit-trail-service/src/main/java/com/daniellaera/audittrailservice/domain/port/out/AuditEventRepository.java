package com.daniellaera.audittrailservice.domain.port.out;

import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventFilter;
import com.daniellaera.audittrailservice.domain.model.PageQuery;
import com.daniellaera.audittrailservice.domain.model.PageResult;

import java.util.List;

/** The append-only store of audit events: no update, no delete. */
public interface AuditEventRepository {

    AuditEvent save(AuditEvent event);

    // newest first; the id breaks ties between events recorded in the same microsecond
    PageResult<AuditEvent> search(AuditEventFilter filter, PageQuery page);

    // oldest first, ties by id. Events recorded before the correlation id existed are matched as they were stored:
    // order and payment events by their entity id (except PAYMENT_INITIATED), STOCK_RESTORED by its payload
    List<AuditEvent> timelineOf(String orderId);

    // newest first
    List<AuditEvent> findByUserEmail(String userEmail);

    // entity type ORDER with this id, newest first
    List<AuditEvent> findOrderEvents(String orderId);
}
