package com.daniellaera.audittrailservice.controller;

import com.daniellaera.audittrailservice.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /audit's filters and GET /audit/orders/{id}/timeline on PostgreSQL, with the schema of the Flyway migrations.
 * The rows are inserted with their recording time, in UTC as the service stores it. Order 65 has both kinds of event:
 * three recorded before the correlation id existed (matched by entity, or by payload for STOCK_RESTORED), the rest
 * with it. Order 650 is there to catch a prefix match.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditQueriesITTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void events() {
        jdbc.update("DELETE FROM audit_events");
        // before the correlation id: no correlation_id column value
        insert(1, "PAYMENT_INITIATED", "PAYMENT", "pi_65", null, "payment-service", "2026-10-20T09:59:58");
        insert(2, "ORDER_CREATED", "ORDER", "65", null, "order-service", "2026-10-20T10:00:00");
        insert(3, "PAYMENT_FAILED", "PAYMENT", "65", null, "payment-service", "2026-10-20T10:00:02");
        insertWithPayload(4, "STOCK_RESTORED", "PRODUCT", "3", null, "inventory-service", "2026-10-20T10:00:03",
                "{\"id\":\"3f2b8c1e\",\"orderId\":65,\"productName\":\"Lamp\",\"quantity\":1}");
        // order 650: must never show up in order 65's timeline
        insert(5, "ORDER_CREATED", "ORDER", "650", null, "order-service", "2026-10-20T10:01:00");
        insertWithPayload(6, "STOCK_RESTORED", "PRODUCT", "3", null, "inventory-service", "2026-10-20T10:01:03",
                "{\"id\":\"9a0c\",\"orderId\":650,\"productName\":\"Lamp\",\"quantity\":1}");
        // with the correlation id: a second order 66, the compensation path
        insert(10, "ORDER_CREATED", "ORDER", "66", "66", "order-service", "2026-10-25T08:00:00");
        insert(11, "STOCK_DEDUCTED", "PRODUCT", "3", "66", "inventory-service", "2026-10-25T08:00:01");
        insert(12, "PAYMENT_FAILED", "PAYMENT", "66", "66", "payment-service", "2026-10-25T08:00:02");
        insert(14, "ORDER_CANCELLED", "ORDER", "66", "66", "order-service", "2026-10-25T08:00:03");
        insert(13, "STOCK_RESTORED", "PRODUCT", "3", "66", "inventory-service", "2026-10-25T08:00:03");
        insert(20, "USER_LOGIN", "USER", "admin@example.com", null, "auth-service", "2026-10-25T08:05:00");
        // past the ids inserted here, for the rows other tests save through JPA
        jdbc.queryForObject("SELECT setval('audit_events_id_seq', 1000)", Long.class);
    }

    @Test
    void timeline_ofANewOrder_isEveryEventWithItsCorrelationId_oldestFirst_tiesByRecordingOrder() throws Exception {
        timeline("66")
                .andExpect(jsonPath("$[*].eventType").value(contains(
                        "ORDER_CREATED", "STOCK_DEDUCTED", "PAYMENT_FAILED", "STOCK_RESTORED", "ORDER_CANCELLED")))
                .andExpect(jsonPath("$[*].correlationId").value(contains("66", "66", "66", "66", "66")));
    }

    @Test
    void timeline_ofAnOrderRecordedBeforeTheCorrelationId_isMatchedByEntityAndByThePayloadOfStockRestored()
            throws Exception {
        // no PAYMENT_INITIATED (its entity is the intent), nothing of order 650
        timeline("65")
                .andExpect(jsonPath("$[*].id").value(contains(2, 3, 4)))
                .andExpect(jsonPath("$[*].eventType").value(contains("ORDER_CREATED", "PAYMENT_FAILED", "STOCK_RESTORED")))
                .andExpect(jsonPath("$[0].correlationId").value(nullValue()));
    }

    @Test
    void timeline_ofAnUnknownOrder_isEmpty() throws Exception {
        timeline("999").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void timeline_ofANonNumericOrderId_isNotFound() throws Exception {
        mockMvc.perform(get("/audit/orders/65%25/timeline")).andExpect(status().isNotFound());
    }

    @Test
    void events_carryTheirRecordingTime_asBeforeAndAsAnInstantInUtc() throws Exception {
        timeline("66")
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-25T08:00:00"))
                .andExpect(jsonPath("$[0].recordedAt").value("2026-10-25T08:00:00Z"));
    }

    @Test
    void list_withoutFilters_isEveryEvent_newestFirst() throws Exception {
        mockMvc.perform(get("/audit").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains(20, 14, 13, 12, 11)))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void list_searchesTheUserEntityServiceAndEventType_caseInsensitively() throws Exception {
        list("q", "650").andExpect(jsonPath("$.content[*].id").value(contains(5)));
        list("q", "ADMIN@").andExpect(jsonPath("$.content[*].id").value(contains(20)));
        list("q", "restored").andExpect(jsonPath("$.content[*].id").value(contains(13, 6, 4)));
        // % and _ are literal characters, not patterns
        list("q", "%").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_filtersByEventTypes_service_order_andRecordingTime() throws Exception {
        mockMvc.perform(get("/audit").param("eventType", "ORDER_CREATED", "ORDER_CANCELLED"))
                .andExpect(jsonPath("$.content[*].id").value(contains(14, 10, 5, 2)));
        list("serviceName", "payment-service").andExpect(jsonPath("$.content[*].id").value(contains(12, 3, 1)));
        list("orderId", "66").andExpect(jsonPath("$.content[*].id").value(contains(14, 13, 12, 11, 10)));
        mockMvc.perform(get("/audit")
                        .param("from", "2026-10-25T08:00:01Z")
                        .param("to", "2026-10-25T08:00:03Z"))
                .andExpect(jsonPath("$.content[*].id").value(contains(12, 11)));
        // the instant's offset counts: 10:00:01+02:00 is 08:00:01 UTC
        mockMvc.perform(get("/audit").param("from", "2026-10-25T10:00:01+02:00").param("eventType", "STOCK_DEDUCTED"))
                .andExpect(jsonPath("$.content[*].id").value(contains(11)));
    }

    @Test
    void list_rejectsAnUnknownEventTypeAndAnUnreadableInstant() throws Exception {
        list("eventType", "NOT_A_TYPE").andExpect(status().isBadRequest());
        list("from", "yesterday").andExpect(status().isBadRequest());
    }

    // were answered 500 (unnamed path variables) until the timeline came
    @Test
    void theOlderLookupsByUserAndByOrder_answer() throws Exception {
        mockMvc.perform(get("/audit/user/{email}", "admin@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(20)));
        mockMvc.perform(get("/audit/order/{orderId}", "66"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(14, 10)));
    }

    @Test
    void list_capsThePageSize() throws Exception {
        list("size", "100000").andExpect(jsonPath("$.size").value(200));
    }

    private ResultActions timeline(String orderId) throws Exception {
        return mockMvc.perform(get("/audit/orders/{orderId}/timeline", orderId)).andExpect(status().isOk());
    }

    private ResultActions list(String name, String value) throws Exception {
        return mockMvc.perform(get("/audit").param(name, value));
    }

    private void insert(long id, String type, String entityType, String entityId, String correlationId,
                        String service, String createdAt) {
        insertWithPayload(id, type, entityType, entityId, correlationId, service, createdAt, "{}");
    }

    private void insertWithPayload(long id, String type, String entityType, String entityId, String correlationId,
                                   String service, String createdAt, String payload) {
        jdbc.update("""
                        INSERT INTO audit_events (id, event_type, user_email, entity_type, entity_id, payload,
                                                  service_name, created_at, correlation_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, type, type.equals("USER_LOGIN") ? "admin@example.com" : "buyer@test.com", entityType, entityId,
                payload, service, LocalDateTime.parse(createdAt), correlationId);
    }
}
