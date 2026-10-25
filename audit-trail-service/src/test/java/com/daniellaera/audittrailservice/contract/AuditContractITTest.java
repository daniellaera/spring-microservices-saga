package com.daniellaera.audittrailservice.contract;

import com.daniellaera.audittrailservice.TestcontainersConfiguration;
import com.daniellaera.audittrailservice.adapter.in.messaging.AuditConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The wire contracts of audit-trail-service, recorded from the code before the hexagonal refactor and kept
 * byte-identical (STRICT): the REST bodies of /audit, and what a consumed audit-topic message becomes in the
 * database (the row, as JSON). Fixtures in src/test/resources/contracts/. -Dcontracts.record=true rewrites them;
 * only ever on code whose behaviour is the reference.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditContractITTest {

    private static final Path CONTRACTS = Path.of("src/test/resources/contracts");
    private static final boolean RECORD = Boolean.getBoolean("contracts.record");
    // Spring's error body carries the time of the request
    private static final CustomComparator IGNORE_TIMESTAMP = new CustomComparator(JSONCompareMode.STRICT,
            new Customization("timestamp", (expected, actual) -> true));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditConsumer auditConsumer;

    @BeforeEach
    void events() {
        jdbc.update("DELETE FROM audit_events");
        insert(1, "PAYMENT_INITIATED", "buyer@test.com", "PAYMENT", "pi_65", null, "payment-service",
                "2026-10-20T09:59:58.123456", "{\"amount\":4990,\"currency\":\"chf\",\"productName\":\"Lamp\"}");
        insert(2, "ORDER_CREATED", "buyer@test.com", "ORDER", "65", null, "order-service",
                "2026-10-20T10:00:00", "{\"orderId\":65,\"productName\":\"Lamp\"}");
        insert(3, "STOCK_RESTORED", "buyer@test.com", "PRODUCT", "3", null, "inventory-service",
                "2026-10-20T10:00:03", "{\"id\":\"3f2b\",\"orderId\":65,\"productName\":\"Lamp\",\"quantity\":1}");
        insert(10, "ORDER_CREATED", "buyer@test.com", "ORDER", "66", "66", "order-service",
                "2026-10-25T08:00:00", "{\"orderId\":66}");
        insert(11, "STOCK_DEDUCTED", "buyer@test.com", "PRODUCT", "3", "66", "inventory-service",
                "2026-10-25T08:00:01.5", "{\"productName\":\"Lamp\",\"quantity\":1}");
        insert(12, "PAYMENT_SUCCEEDED", "buyer@test.com", "PAYMENT", "66", "66", "payment-service",
                "2026-10-25T08:00:02", "{\"orderId\":66,\"status\":\"SUCCESS\"}");
        insert(20, "USER_LOGIN", "admin@example.com", "USER", "admin@example.com", null, "auth-service",
                "2026-10-25T08:05:00", "\"admin@example.com\"");
        jdbc.queryForObject("SELECT setval('audit_events_id_seq', 1000)", Long.class);
    }

    @Test
    void list_firstPage() throws Exception {
        assertResponse("rest-list-page.json", get("/audit").param("page", "0").param("size", "3"));
    }

    @Test
    void list_filtered() throws Exception {
        assertResponse("rest-list-filtered.json", get("/audit")
                .param("eventType", "ORDER_CREATED", "PAYMENT_SUCCEEDED").param("q", "6"));
    }

    @Test
    void timeline_newAndLegacyEvents() throws Exception {
        assertResponse("rest-timeline-66.json", get("/audit/orders/66/timeline"));
        assertResponse("rest-timeline-65.json", get("/audit/orders/65/timeline"));
    }

    @Test
    void lookupsByUserAndByOrder() throws Exception {
        assertResponse("rest-by-user.json", get("/audit/user/admin@example.com"));
        assertResponse("rest-by-order.json", get("/audit/order/66"));
    }

    @Test
    void unknownEventType_isABadRequest() throws Exception {
        assertResponse("rest-bad-event-type.json", get("/audit").param("eventType", "NOT_A_TYPE"));
    }

    @Test
    void consumedSagaMessage_isStoredAsThisRow() throws Exception {
        consume("audit-message-saga.json");
        assertStoredRow("stored-saga-message.json");
    }

    @Test
    void consumedMessageWithoutCorrelationId_isStoredAsThisRow() throws Exception {
        consume("audit-message-legacy.json");
        assertStoredRow("stored-legacy-message.json");
    }

    private void consume(String fixture) {
        jdbc.update("DELETE FROM audit_events");
        auditConsumer.consume(read(fixture));
    }

    // every column but the generated id and the recording time, which say nothing about the mapping
    private void assertStoredRow(String fixture) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT event_type, user_email, entity_type, entity_id, payload, ip_address, service_name, correlation_id
                FROM audit_events""");
        Map<String, Object> row = new LinkedHashMap<>(rows.getFirst());
        row.put("rows", rows.size());
        check(fixture, JsonMapper.builder().build().writeValueAsString(row), JSONCompareMode.STRICT);
    }

    private void assertResponse(String fixture, MockHttpServletRequestBuilder request) throws Exception {
        var response = mockMvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        String actual = JsonMapper.builder().build().writeValueAsString(Map.of(
                "status", response.getStatus(),
                "body", JsonMapper.builder().build().readTree(body)));
        check(fixture, actual, IGNORE_TIMESTAMP);
    }

    private static void check(String fixture, String actual, org.skyscreamer.jsonassert.comparator.JSONComparator mode)
            throws Exception {
        if (RECORD) {
            Files.createDirectories(CONTRACTS);
            Files.writeString(CONTRACTS.resolve(fixture),
                    JsonMapper.builder().build().writerWithDefaultPrettyPrinter()
                            .writeValueAsString(JsonMapper.builder().build().readTree(actual)) + "\n");
            return;
        }
        JSONAssert.assertEquals(read(fixture), actual, mode);
    }

    private static void check(String fixture, String actual, JSONCompareMode mode) throws Exception {
        check(fixture, actual, new CustomComparator(mode));
    }

    private static String read(String fixture) {
        try {
            return Files.readString(CONTRACTS.resolve(fixture));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void insert(long id, String type, String user, String entityType, String entityId, String correlationId,
                        String service, String createdAt, String payload) {
        jdbc.update("""
                        INSERT INTO audit_events (id, event_type, user_email, entity_type, entity_id, payload,
                                                  service_name, created_at, correlation_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, type, user, entityType, entityId, payload, service, LocalDateTime.parse(createdAt), correlationId);
    }
}
