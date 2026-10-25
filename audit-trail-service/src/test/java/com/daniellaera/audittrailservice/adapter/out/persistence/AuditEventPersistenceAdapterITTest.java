package com.daniellaera.audittrailservice.adapter.out.persistence;

import com.daniellaera.audittrailservice.TestcontainersConfiguration;
import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventFilter;
import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import com.daniellaera.audittrailservice.domain.model.PageQuery;
import com.daniellaera.audittrailservice.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The adapter on PostgreSQL with the Flyway schema: saving, the newest-first page and the timeline's legacy rows. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, AuditEventPersistenceAdapter.class})
class AuditEventPersistenceAdapterITTest {

    @Autowired
    private AuditEventPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void empty() {
        jdbc.update("DELETE FROM audit_events");
    }

    @Test
    void save_assignsTheIdAndTheRecordingTimeInUtc() {
        AuditEvent saved = adapter.save(AuditEvent.of(AuditEventType.ORDER_CREATED, "buyer@test.com", "ORDER", "42",
                "{}", "order-service", "42"));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(10, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(saved).usingRecursiveComparison().ignoringFields("id", "createdAt").isEqualTo(
                AuditEvent.of(AuditEventType.ORDER_CREATED, "buyer@test.com", "ORDER", "42", "{}", "order-service", "42"));
        assertThat(jdbc.queryForObject("SELECT correlation_id FROM audit_events", String.class)).isEqualTo("42");
    }

    @Test
    void search_isNewestFirst_tiesByIdDescending_paged() {
        insert(1, "ORDER_CREATED", "ORDER", "1", null, "2026-10-25T08:00:00");
        insert(2, "ORDER_CREATED", "ORDER", "2", null, "2026-10-25T08:00:01");
        insert(3, "ORDER_CREATED", "ORDER", "3", null, "2026-10-25T08:00:01");

        PageResult<AuditEvent> first = adapter.search(AuditEventFilter.none(), new PageQuery(0, 2));
        PageResult<AuditEvent> second = adapter.search(AuditEventFilter.none(), new PageQuery(1, 2));

        assertThat(first.content()).extracting(AuditEvent::id).containsExactly(3L, 2L);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.content()).extracting(AuditEvent::id).containsExactly(1L);
    }

    @Test
    void timeline_hasTheCorrelatedEventsAndTheLegacyOnes_oldestFirst() {
        insert(1, "PAYMENT_INITIATED", "PAYMENT", "pi_65", null, "2026-10-20T09:59:58");
        insert(2, "ORDER_CREATED", "ORDER", "65", null, "2026-10-20T10:00:00");
        insertPayload(3, "STOCK_RESTORED", "PRODUCT", "3", null, "2026-10-20T10:00:03", "{\"id\":\"a\",\"orderId\":65,\"quantity\":1}");
        insertPayload(4, "STOCK_RESTORED", "PRODUCT", "3", null, "2026-10-20T10:00:04", "{\"id\":\"b\",\"orderId\":650,\"quantity\":1}");
        insert(5, "ORDER_CONFIRMED", "ORDER", "65", "65", "2026-10-25T08:00:00");

        assertThat(adapter.timelineOf("65")).extracting(AuditEvent::id).containsExactly(2L, 3L, 5L);
    }

    @Test
    void eventsOfAUser_andOfAnOrder_newestFirst() {
        insert(1, "ORDER_CREATED", "ORDER", "7", "7", "2026-10-25T08:00:00");
        insert(2, "PAYMENT_SUCCEEDED", "PAYMENT", "7", "7", "2026-10-25T08:00:01");
        insert(3, "ORDER_CONFIRMED", "ORDER", "7", "7", "2026-10-25T08:00:02");

        assertThat(adapter.findByUserEmail("buyer@test.com")).extracting(AuditEvent::id).containsExactly(3L, 2L, 1L);
        assertThat(adapter.findOrderEvents("7")).extracting(AuditEvent::id).containsExactly(3L, 1L);
        assertThat(adapter.findByUserEmail("nobody@test.com")).isEqualTo(List.of());
    }

    private void insert(long id, String type, String entityType, String entityId, String correlationId, String at) {
        insertPayload(id, type, entityType, entityId, correlationId, at, "{}");
    }

    private void insertPayload(long id, String type, String entityType, String entityId, String correlationId,
                               String at, String payload) {
        jdbc.update("""
                        INSERT INTO audit_events (id, event_type, user_email, entity_type, entity_id, payload,
                                                  service_name, created_at, correlation_id)
                        VALUES (?, ?, 'buyer@test.com', ?, ?, ?, 'order-service', ?, ?)""",
                id, type, entityType, entityId, payload, LocalDateTime.parse(at), correlationId);
    }
}
