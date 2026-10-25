package com.daniellaera.audittrailservice.adapter.out.persistence;

import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventFilter;
import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import com.daniellaera.audittrailservice.domain.model.PageQuery;
import com.daniellaera.audittrailservice.domain.model.PageResult;
import com.daniellaera.audittrailservice.domain.port.out.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuditEventPersistenceAdapter implements AuditEventRepository {

    // newest first; the id breaks ties between events recorded in the same microsecond
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AuditEventJpaRepository jpaRepository;

    @Override
    public AuditEvent save(AuditEvent event) {
        return AuditEventJpaMapper.toDomain(jpaRepository.save(AuditEventJpaMapper.toNewEntity(event)));
    }

    @Override
    public PageResult<AuditEvent> search(AuditEventFilter filter, PageQuery page) {
        Page<AuditEventJpaEntity> found = jpaRepository.findAll(AuditEventSpecifications.matching(filter),
                PageRequest.of(page.page(), page.size(), NEWEST_FIRST));
        return new PageResult<>(found.getContent().stream().map(AuditEventJpaMapper::toDomain).toList(), page,
                found.getTotalElements());
    }

    // orderId: digits only (the controller's path pattern), so it is safe inside the LIKE pattern
    @Override
    public List<AuditEvent> timelineOf(String orderId) {
        return toDomain(jpaRepository.findTimeline(orderId, AuditEventType.PAYMENT_INITIATED,
                AuditEventType.STOCK_RESTORED, "%\"orderId\":" + orderId + ",%"));
    }

    @Override
    public List<AuditEvent> findByUserEmail(String userEmail) {
        return toDomain(jpaRepository.findByUserEmailOrderByCreatedAtDesc(userEmail));
    }

    @Override
    public List<AuditEvent> findOrderEvents(String orderId) {
        return toDomain(jpaRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("ORDER", orderId));
    }

    private static List<AuditEvent> toDomain(List<AuditEventJpaEntity> entities) {
        return entities.stream().map(AuditEventJpaMapper::toDomain).toList();
    }
}
