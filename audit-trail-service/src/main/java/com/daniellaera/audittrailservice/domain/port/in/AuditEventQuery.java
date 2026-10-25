package com.daniellaera.audittrailservice.domain.port.in;

import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventFilter;
import com.daniellaera.audittrailservice.domain.model.PageQuery;
import com.daniellaera.audittrailservice.domain.model.PageResult;

import java.util.List;

public interface AuditEventQuery {

    // newest first
    PageResult<AuditEvent> search(AuditEventFilter filter, PageQuery page);

    // one order's saga, oldest first; orderId is digits only
    List<AuditEvent> timelineOf(String orderId);

    // newest first
    List<AuditEvent> eventsOfUser(String userEmail);

    // the events whose entity is the order, newest first
    List<AuditEvent> eventsOfOrder(String orderId);
}
