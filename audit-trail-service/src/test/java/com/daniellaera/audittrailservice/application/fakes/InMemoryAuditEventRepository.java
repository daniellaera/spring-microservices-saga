package com.daniellaera.audittrailservice.application.fakes;

import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventFilter;
import com.daniellaera.audittrailservice.domain.model.PageQuery;
import com.daniellaera.audittrailservice.domain.model.PageResult;
import com.daniellaera.audittrailservice.domain.port.out.AuditEventRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores what it is given and records what it was asked. The queries' filtering and ordering belong to the
 * persistence adapter and are tested there: here each query answers a list set by the test.
 */
public class InMemoryAuditEventRepository implements AuditEventRepository {

    public record Search(AuditEventFilter filter, PageQuery page) {}

    private final List<AuditEvent> saved = new ArrayList<>();
    private final List<Object> queries = new ArrayList<>();
    private List<AuditEvent> answer = List.of();
    private RuntimeException failure;

    public void answer(List<AuditEvent> events) {
        this.answer = List.copyOf(events);
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public AuditEvent save(AuditEvent event) {
        if (failure != null) {
            throw failure;
        }
        AuditEvent stored = new AuditEvent((long) saved.size() + 1, event.eventType(), event.userEmail(),
                event.entityType(), event.entityId(), event.payload(), event.serviceName(), event.correlationId(),
                LocalDateTime.of(2026, 10, 25, 8, 0));
        saved.add(stored);
        return stored;
    }

    @Override
    public PageResult<AuditEvent> search(AuditEventFilter filter, PageQuery page) {
        queries.add(new Search(filter, page));
        return new PageResult<>(answer, page, answer.size());
    }

    @Override
    public List<AuditEvent> timelineOf(String orderId) {
        queries.add("timeline " + orderId);
        return answer;
    }

    @Override
    public List<AuditEvent> findByUserEmail(String userEmail) {
        queries.add("user " + userEmail);
        return answer;
    }

    @Override
    public List<AuditEvent> findOrderEvents(String orderId) {
        queries.add("order " + orderId);
        return answer;
    }

    public List<AuditEvent> saved() {
        return List.copyOf(saved);
    }

    public List<Object> queries() {
        return List.copyOf(queries);
    }
}
