package com.daniellaera.audittrailservice.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * The filters of GET /audit, every one optional: q is a case-insensitive search in the user, event type, entity,
 * service and correlation id; eventTypes any of them; orderId the saga's correlation id; [from, to) the recording
 * time.
 */
public record AuditEventFilter(
        String q,
        List<AuditEventType> eventTypes,
        String serviceName,
        String userEmail,
        String orderId,
        Instant from,
        Instant to
) {

    public static AuditEventFilter none() {
        return new AuditEventFilter(null, null, null, null, null, null, null);
    }
}
