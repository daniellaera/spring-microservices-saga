package com.daniellaera.audittrailservice.adapter.out.persistence;

import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventJpaMapperTest {

    @Test
    void aNewEvent_becomesARowWithEveryField_andNoIdOrTimeYet() {
        AuditEventJpaEntity entity = AuditEventJpaMapper.toNewEntity(AuditEvent.of(AuditEventType.STOCK_DEDUCTED,
                "buyer@test.com", "PRODUCT", "3", "{\"quantity\":1}", "inventory-service", "66"));

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getIpAddress()).isNull();
        assertThat(AuditEventJpaMapper.toDomain(entity)).isEqualTo(new AuditEvent(null, AuditEventType.STOCK_DEDUCTED,
                "buyer@test.com", "PRODUCT", "3", "{\"quantity\":1}", "inventory-service", "66", null));
    }
}
