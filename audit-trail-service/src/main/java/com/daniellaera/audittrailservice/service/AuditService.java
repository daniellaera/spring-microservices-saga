package com.daniellaera.audittrailservice.service;

import com.daniellaera.audittrailservice.dto.AuditEventDTO;
import com.daniellaera.audittrailservice.dto.AuditEventFilter;
import com.daniellaera.audittrailservice.dto.AuditMessage;
import com.daniellaera.audittrailservice.enums.AuditEventType;
import com.daniellaera.audittrailservice.model.AuditEvent;
import com.daniellaera.audittrailservice.repository.AuditEventRepository;
import com.daniellaera.audittrailservice.repository.AuditEventSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional
    public void record(AuditMessage message) {
        try {
            AuditEvent event = AuditEvent.of(
                    AuditEventType.valueOf(message.eventType()),
                    message.userEmail(),
                    message.entityType(),
                    message.entityId(),
                    message.payload(),
                    message.serviceName(),
                    message.correlationId()
            );
            auditEventRepository.save(event);
            log.info("=== Audit: recorded {} for {} entity={}",
                    message.eventType(),
                    message.userEmail(),
                    message.entityId());
        } catch (Exception e) {
            log.error("=== Audit: failed to record event: {}", e.getMessage());
            // never throw — audit failure must not break business flow
        }
    }

    // newest first; the id breaks ties between events recorded in the same microsecond
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    public Page<AuditEventDTO> search(AuditEventFilter filter, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return auditEventRepository
                .findAll(AuditEventSpecifications.matching(filter), pageable)
                .map(this::toDTO);
    }

    // orderId: digits only (the controller's path pattern), so it is safe inside the LIKE pattern
    public List<AuditEventDTO> getTimeline(String orderId) {
        return auditEventRepository
                .findTimeline(orderId, AuditEventType.PAYMENT_INITIATED, AuditEventType.STOCK_RESTORED,
                        "%\"orderId\":" + orderId + ",%")
                .stream().map(this::toDTO).toList();
    }

    public List<AuditEventDTO> getByUser(String userEmail) {
        return auditEventRepository
                .findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream().map(this::toDTO).toList();
    }

    public List<AuditEventDTO> getByOrder(String orderId) {
        return auditEventRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("ORDER", orderId)
                .stream().map(this::toDTO).toList();
    }

    private AuditEventDTO toDTO(AuditEvent e) {
        return new AuditEventDTO(
                e.getId(),
                e.getEventType().name(),
                e.getUserEmail(),
                e.getEntityType(),
                e.getEntityId(),
                e.getPayload(),
                e.getServiceName(),
                e.getCreatedAt(),
                e.getCorrelationId(),
                e.getCreatedAt() != null ? e.getCreatedAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}
