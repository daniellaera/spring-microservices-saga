package com.daniellaera.audittrailservice.repository;

import com.daniellaera.audittrailservice.enums.AuditEventType;
import com.daniellaera.audittrailservice.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId);

    List<AuditEvent> findByEventTypeOrderByCreatedAtDesc(AuditEventType eventType);

    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditEvent> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    List<AuditEvent> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to);
}
