package com.daniellaera.audittrailservice.adapter.in.web;

import com.daniellaera.audittrailservice.adapter.in.web.dto.AuditEventDTO;
import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.ZoneOffset;
import java.util.List;

final class AuditWebMapper {

    // The page JSON has always been Spring Data's, sorted: the store sorts newest first, ties by id
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private AuditWebMapper() {
    }

    static Page<AuditEventDTO> toPage(PageResult<AuditEvent> result) {
        return new PageImpl<>(toDtos(result.content()),
                PageRequest.of(result.page().page(), result.page().size(), NEWEST_FIRST), result.totalElements());
    }

    static List<AuditEventDTO> toDtos(List<AuditEvent> events) {
        return events.stream().map(AuditWebMapper::toDto).toList();
    }

    // createdAt is UTC: recordedAt is the same instant with its offset
    static AuditEventDTO toDto(AuditEvent e) {
        return new AuditEventDTO(
                e.id(),
                e.eventType().name(),
                e.userEmail(),
                e.entityType(),
                e.entityId(),
                e.payload(),
                e.serviceName(),
                e.createdAt(),
                e.correlationId(),
                e.createdAt() != null ? e.createdAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}
