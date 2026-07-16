package com.daniellaera.audittrailservice.model;

import com.daniellaera.audittrailservice.enums.AuditEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_user_email", columnList = "user_email"),
                @Index(name = "idx_audit_event_type", columnList = "event_type"),
                @Index(name = "idx_audit_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_entity_id", columnList = "entity_id")
        }
)
@Getter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String entityType; // ORDER, PRODUCT, USER, PAYMENT

    @Column
    private String entityId; // orderId, productId, userId

    @Column(columnDefinition = "TEXT")
    private String payload; // JSON snapshot of the event

    @Column
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String serviceName; // which service emitted this

    // NO setters — append-only, immutable after creation
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // builder pattern for creation only
    public static AuditEvent of(
            AuditEventType eventType,
            String userEmail,
            String entityType,
            String entityId,
            String payload,
            String serviceName) {
        AuditEvent event = new AuditEvent();
        event.eventType = eventType;
        event.userEmail = userEmail;
        event.entityType = entityType;
        event.entityId = entityId;
        event.payload = payload;
        event.serviceName = serviceName;
        return event;
    }
}
