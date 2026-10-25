package com.daniellaera.audittrailservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTest {

    @Test
    void ofWhatAServiceSent_isNotStoredYet() {
        AuditEvent event = AuditEvent.of(AuditEventType.STOCK_DEDUCTED, "buyer@test.com", "PRODUCT", "3", "{}",
                "inventory-service", "66");

        assertThat(event.id()).isNull();
        assertThat(event.createdAt()).isNull();
        assertThat(event.eventType()).isEqualTo(AuditEventType.STOCK_DEDUCTED);
        assertThat(event.userEmail()).isEqualTo("buyer@test.com");
        assertThat(event.entityType()).isEqualTo("PRODUCT");
        assertThat(event.entityId()).isEqualTo("3");
        assertThat(event.payload()).isEqualTo("{}");
        assertThat(event.serviceName()).isEqualTo("inventory-service");
        assertThat(event.correlationId()).isEqualTo("66");
    }

    @Test
    void anEventOutsideASaga_hasNoCorrelationId() {
        assertThat(AuditEvent.of(AuditEventType.USER_LOGIN, "admin@example.com", "USER", "admin@example.com",
                "\"admin@example.com\"", "auth-service", null).correlationId()).isNull();
    }
}
