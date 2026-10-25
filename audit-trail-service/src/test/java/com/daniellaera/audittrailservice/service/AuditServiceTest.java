package com.daniellaera.audittrailservice.service;

import com.daniellaera.audittrailservice.dto.AuditEventDTO;
import com.daniellaera.audittrailservice.dto.AuditEventFilter;
import com.daniellaera.audittrailservice.dto.AuditMessage;
import com.daniellaera.audittrailservice.enums.AuditEventType;
import com.daniellaera.audittrailservice.model.AuditEvent;
import com.daniellaera.audittrailservice.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

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
                "{}", "order-service", "2026-01-01T00:00:00", "42");

        auditService.record(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuditEventType.ORDER_CREATED);
        assertThat(captor.getValue().getUserEmail()).isEqualTo("buyer@test.com");
        assertThat(captor.getValue().getEntityId()).isEqualTo("42");
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("42");
    }

    @Test
    void record_messageWithoutCorrelationId_savesEventWithoutOne() {
        // sent before the correlation id existed, or outside a saga
        AuditMessage message = new AuditMessage(
                "USER_LOGIN", "buyer@test.com", "USER", "buyer@test.com",
                "{}", "auth-service", "2026-01-01T00:00:00", null);

        auditService.record(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getCorrelationId()).isNull();
    }

    @Test
    void record_invalidEventType_logsErrorAndDoesNotThrow() {
        AuditMessage message = new AuditMessage(
                "NOT_A_REAL_EVENT", "buyer@test.com", "ORDER", "42",
                "{}", "order-service", "2026-01-01T00:00:00", null);

        auditService.record(message);

        verify(auditEventRepository, never()).save(any());
    }

    @Test
    void search_returnsAPageOfTheMatchingEvents_newestFirst() {
        AuditEvent event = AuditEvent.of(AuditEventType.ORDER_CREATED, "buyer@test.com", "ORDER", "1", "{}", "order-service");
        when(auditEventRepository.findAll(ArgumentMatchers.<Specification<AuditEvent>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<AuditEventDTO> result = auditService.search(AuditEventFilter.none(), 2, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).eventType()).isEqualTo("ORDER_CREATED");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findAll(ArgumentMatchers.<Specification<AuditEvent>>any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    @Test
    void getTimeline_matchesLegacyStockRestoredEventsOnTheOrderIdInTheirPayload() {
        when(auditEventRepository.findTimeline(any(), any(), any(), any())).thenReturn(List.of());

        auditService.getTimeline("65");

        verify(auditEventRepository).findTimeline("65", AuditEventType.PAYMENT_INITIATED, AuditEventType.STOCK_RESTORED,
                "%\"orderId\":65,%");
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
