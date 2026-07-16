package com.daniellaera.audittrailservice.service;

import com.daniellaera.audittrailservice.dto.AuditEventDTO;
import com.daniellaera.audittrailservice.dto.AuditMessage;
import com.daniellaera.audittrailservice.enums.AuditEventType;
import com.daniellaera.audittrailservice.model.AuditEvent;
import com.daniellaera.audittrailservice.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void record_validMessage_savesEvent() {
        AuditMessage message = new AuditMessage(
                "ORDER_CREATED", "buyer@test.com", "ORDER", "42",
                "{}", "order-service", "2026-01-01T00:00:00");

        auditService.record(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuditEventType.ORDER_CREATED);
        assertThat(captor.getValue().getUserEmail()).isEqualTo("buyer@test.com");
        assertThat(captor.getValue().getEntityId()).isEqualTo("42");
    }

    @Test
    void record_invalidEventType_logsErrorAndDoesNotThrow() {
        AuditMessage message = new AuditMessage(
                "NOT_A_REAL_EVENT", "buyer@test.com", "ORDER", "42",
                "{}", "order-service", "2026-01-01T00:00:00");

        auditService.record(message);

        verify(auditEventRepository, never()).save(any());
    }

    @Test
    void getAll_returnsPagedResults() {
        AuditEvent event = AuditEvent.of(AuditEventType.ORDER_CREATED, "buyer@test.com", "ORDER", "1", "{}", "order-service");
        when(auditEventRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<AuditEventDTO> result = auditService.getAll(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).eventType()).isEqualTo("ORDER_CREATED");
    }

    @Test
    void getByUser_filtersByUserEmail() {
        AuditEvent event = AuditEvent.of(AuditEventType.USER_LOGIN, "buyer@test.com", "USER", "buyer@test.com", "{}", "auth-service");
        when(auditEventRepository.findByUserEmailOrderByCreatedAtDesc("buyer@test.com"))
                .thenReturn(List.of(event));

        List<AuditEventDTO> result = auditService.getByUser("buyer@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userEmail()).isEqualTo("buyer@test.com");
    }

    @Test
    void getByOrder_filtersByOrderEntityType() {
        AuditEvent event = AuditEvent.of(AuditEventType.ORDER_CREATED, "buyer@test.com", "ORDER", "42", "{}", "order-service");
        when(auditEventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("ORDER", "42"))
                .thenReturn(List.of(event));

        List<AuditEventDTO> result = auditService.getByOrder("42");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityType()).isEqualTo("ORDER");
        assertThat(result.get(0).entityId()).isEqualTo("42");
    }
}
