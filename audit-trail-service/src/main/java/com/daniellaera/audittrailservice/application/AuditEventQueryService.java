package com.daniellaera.audittrailservice.application;

import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventFilter;
import com.daniellaera.audittrailservice.domain.model.PageQuery;
import com.daniellaera.audittrailservice.domain.model.PageResult;
import com.daniellaera.audittrailservice.domain.port.in.AuditEventQuery;
import com.daniellaera.audittrailservice.domain.port.out.AuditEventRepository;

import java.util.List;

public class AuditEventQueryService implements AuditEventQuery {

    private final AuditEventRepository repository;

    public AuditEventQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<AuditEvent> search(AuditEventFilter filter, PageQuery page) {
        return repository.search(filter, page);
    }

    @Override
    public List<AuditEvent> timelineOf(String orderId) {
        return repository.timelineOf(orderId);
    }

    @Override
    public List<AuditEvent> eventsOfUser(String userEmail) {
        return repository.findByUserEmail(userEmail);
    }

    @Override
    public List<AuditEvent> eventsOfOrder(String orderId) {
        return repository.findOrderEvents(orderId);
    }
}
