package com.daniellaera.audittrailservice.service;

import com.daniellaera.audittrailservice.dto.AuditEventDTO;
import com.daniellaera.audittrailservice.dto.AuditMessage;
import com.daniellaera.audittrailservice.enums.AuditEventType;
import com.daniellaera.audittrailservice.model.AuditEvent;
import com.daniellaera.audittrailservice.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    message.serviceName()
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

    public Page<AuditEventDTO> getAll(Pageable pageable) {
        return auditEventRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toDTO);
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
                e.getCreatedAt()
        );
    }
}
