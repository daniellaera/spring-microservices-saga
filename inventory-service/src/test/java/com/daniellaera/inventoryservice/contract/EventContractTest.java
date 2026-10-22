package com.daniellaera.inventoryservice.contract;

import com.daniellaera.inventoryservice.NoCachingConfiguration;
import com.daniellaera.inventoryservice.adapter.out.messaging.KafkaAuditPublisher;
import com.daniellaera.inventoryservice.adapter.out.messaging.KafkaInventoryResultPublisher;
import com.daniellaera.inventoryservice.adapter.out.messaging.TransactionAwareKafkaAuditTrail;
import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.dto.OrderEvent;
import com.daniellaera.inventoryservice.dto.OrderItemEvent;
import com.daniellaera.inventoryservice.dto.PaymentEvent;
import com.daniellaera.inventoryservice.dto.ProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Characterizes the JSON wire contracts of inventory-service against the fixtures in
 * src/test/resources/contracts, which were generated from the code as it is before
 * the hexagonal refactor. The fixtures must not change during the refactor.
 * <p>
 * Uses the application's auto-configured ObjectMapper. Besides JSONAssert (strict),
 * produced JSON is compared as text so number scale (49.90 vs 49.9) and property
 * order are pinned too. The audit envelope is a Map.of, so only its payload is
 * compared as text.
 */
@JsonTest
@Import(NoCachingConfiguration.class)
class EventContractTest {

    // Envelope timestamps come from LocalDateTime.now()
    private static final CustomComparator IGNORE_TIMESTAMP = new CustomComparator(JSONCompareMode.STRICT,
            new Customization("timestamp", (expected, actual) -> true));

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 15, 9, 30, 0, 123456000);

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
    }

    // ─── inbound events ──────────────────────────────────────────────────────

    @Test
    void orderEvent_multiItem_isRead() {
        OrderEvent event = objectMapper.readValue(fixture("order-event-multi-item.json"), OrderEvent.class);

        assertThat(event).isEqualTo(new OrderEvent(42L, "MacBook Pro", 1, new BigDecimal("1299.99"),
                new BigDecimal("1399.79"), "user@test.com", "pi_3QxYz123", multiItems()));
    }

    @Test
    void orderEvent_legacySingleItem_isReadWithoutItems() {
        OrderEvent event = objectMapper.readValue(fixture("order-event-legacy-single-item.json"), OrderEvent.class);

        assertThat(event).isEqualTo(new OrderEvent(7L, "Mechanical Keyboard", 2, new BigDecimal("89.50"),
                new BigDecimal("179.00"), "buyer@test.com", "pi_legacy7", null));
    }

    @Test
    void paymentEvent_multiItem_isRead() {
        PaymentEvent event = objectMapper.readValue(fixture("payment-event-multi-item.json"), PaymentEvent.class);

        assertThat(event).isEqualTo(new PaymentEvent(42L, "MacBook Pro", 1, "SUCCESS", new BigDecimal("1299.99"),
                new BigDecimal("1399.79"), "user@test.com", multiItems()));
    }

    @Test
    void paymentEvent_legacySingleItem_isReadWithoutItems() {
        PaymentEvent event = objectMapper.readValue(fixture("payment-event-legacy-single-item.json"), PaymentEvent.class);

        assertThat(event).isEqualTo(new PaymentEvent(7L, "Mechanical Keyboard", 2, "FAILED", new BigDecimal("89.50"),
                new BigDecimal("179.00"), "buyer@test.com", null));
    }

    // ─── inventory-topic ─────────────────────────────────────────────────────

    @Test
    void inventoryResult_approvedMultiItem_isSentToInventoryTopicWithoutKey() throws Exception {
        resultPublisher().publish(multiItemCommand(), ReservationDecision.APPROVED);

        assertInventoryResult("inventory-result-approved-multi-item.json");
    }

    @Test
    void inventoryResult_rejectedLegacySingleItem_carriesTheResolvedItemAndQuantityZero() throws Exception {
        resultPublisher().publish(legacySingleItemCommand(), ReservationDecision.REJECTED);

        assertInventoryResult("inventory-result-rejected.json");
    }

    // ─── audit-topic ─────────────────────────────────────────────────────────

    @Test
    void auditStockDeducted_payloadIsTheOrderItem() throws Exception {
        List<OrderLine> lines = multiItemCommand().lines();

        auditTrail().stockDeducted(macbook(9), lines.get(0), "user@test.com");
        auditTrail().stockDeducted(usbHub(48), lines.get(1), "user@test.com");

        List<String> audits = sentAuditMessages(2);
        JSONAssert.assertEquals(fixture("audit-stock-deducted.json"), audits.get(0), IGNORE_TIMESTAMP);
        assertThat(payloadOf(audits.get(0))).isEqualTo(
                "{\"productName\":\"MacBook Pro\",\"quantity\":1,\"price\":1299.99,\"totalAmount\":1299.99}");
        assertThat(payloadOf(audits.get(1))).isEqualTo(
                "{\"productName\":\"USB-C Hub\",\"quantity\":2,\"price\":49.90,\"totalAmount\":99.80}");
    }

    @Test
    void auditStockRestored_payloadIsTheReservation() throws Exception {
        auditTrail().stockRestored(keyboard(3), keyboardReservation(), "buyer@test.com");

        String audit = sentAuditMessages(1).get(0);
        JSONAssert.assertEquals(fixture("audit-stock-restored.json"), audit, IGNORE_TIMESTAMP);
        assertThat(payloadOf(audit)).isEqualTo(compact(payloadIn("audit-stock-restored.json")));
    }

    @Test
    void auditProductCreated_payloadIsTheSavedProduct() throws Exception {
        auditTrail().productCreated(usbHub(50));

        String audit = sentAuditMessages(1).get(0);
        JSONAssert.assertEquals(fixture("audit-product-created.json"), audit, IGNORE_TIMESTAMP);
        assertThat(payloadOf(audit)).isEqualTo(compact(payloadIn("audit-product-created.json")));
    }

    @Test
    void auditProductRestocked_payloadIsTheSavedProduct() throws Exception {
        auditTrail().productRestocked(macbook(15));

        String audit = sentAuditMessages(1).get(0);
        JSONAssert.assertEquals(fixture("audit-product-restocked.json"), audit, IGNORE_TIMESTAMP);
        assertThat(payloadOf(audit)).isEqualTo(compact(payloadIn("audit-product-restocked.json")));
    }

    // ─── REST ────────────────────────────────────────────────────────────────

    @Test
    void productDtoList_serialization() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(
                new ProductDTO(11L, "MacBook Pro", 10, new BigDecimal("1299.99"), CREATED_AT),
                new ProductDTO(12L, "USB-C Hub", 50, new BigDecimal("49.90"), LocalDateTime.of(2026, 2, 1, 8, 0))));

        String expected = fixture("product-list.json");
        JSONAssert.assertEquals(expected, json, JSONCompareMode.STRICT);
        assertThat(json).isEqualTo(compact(expected));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private KafkaInventoryResultPublisher resultPublisher() {
        return new KafkaInventoryResultPublisher(kafkaTemplate, objectMapper);
    }

    // The publisher is not proxied, so @Async does not apply and the message is sent synchronously.
    // No transaction is active, so the trail publishes right away.
    private TransactionAwareKafkaAuditTrail auditTrail() {
        return new TransactionAwareKafkaAuditTrail(new KafkaAuditPublisher(kafkaTemplate, objectMapper));
    }

    private void assertInventoryResult(String fixtureName) throws Exception {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("inventory-topic"), message.capture());
        verifyNoMoreInteractions(kafkaTemplate);

        String expected = fixture(fixtureName);
        JSONAssert.assertEquals(expected, message.getValue(), JSONCompareMode.STRICT);
        assertThat(message.getValue()).isEqualTo(compact(expected));
    }

    private List<String> sentAuditMessages(int count) {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(count)).send(eq("audit-topic"), message.capture());
        verifyNoMoreInteractions(kafkaTemplate);
        message.getAllValues().forEach(sent ->
                assertThat(objectMapper.readValue(sent, Map.class)).containsKey("timestamp"));
        return message.getAllValues();
    }

    private String payloadOf(String auditMessage) {
        return (String) objectMapper.readValue(auditMessage, Map.class).get("payload");
    }

    private String payloadIn(String auditFixture) {
        return payloadOf(fixture(auditFixture));
    }

    // The command for order-event-multi-item.json
    private static ReserveStockCommand multiItemCommand() {
        return new ReserveStockCommand(42L, "MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1399.79"),
                "user@test.com", "pi_3QxYz123", List.of(
                        new OrderLine("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")),
                        new OrderLine("USB-C Hub", 2, new BigDecimal("49.90"), new BigDecimal("99.80"))));
    }

    // The command for order-event-legacy-single-item.json: the single line is resolved from the legacy fields
    private static ReserveStockCommand legacySingleItemCommand() {
        return new ReserveStockCommand(7L, "Mechanical Keyboard", 2, new BigDecimal("89.50"), new BigDecimal("179.00"),
                "buyer@test.com", "pi_legacy7", List.of(
                        new OrderLine("Mechanical Keyboard", 2, new BigDecimal("89.50"), new BigDecimal("179.00"))));
    }

    private static Product macbook(int quantity) {
        return Product.restore(11L, "MacBook Pro", quantity, new BigDecimal("1299.99"), CREATED_AT);
    }

    private static Product usbHub(int quantity) {
        return Product.restore(12L, "USB-C Hub", quantity, new BigDecimal("49.90"), CREATED_AT);
    }

    private static Product keyboard(int quantity) {
        return Product.restore(13L, "Mechanical Keyboard", quantity, new BigDecimal("89.50"), CREATED_AT);
    }

    private static StockReservation keyboardReservation() {
        return new StockReservation(UUID.fromString("3f2b8c1e-5d4a-4e7b-9c1f-2a6d8e0b4c71"), 7L,
                "Mechanical Keyboard", 2, LocalDateTime.of(2026, 10, 22, 10, 0, 5, 250000000));
    }

    private static List<OrderItemEvent> multiItems() {
        return List.of(
                new OrderItemEvent("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")),
                new OrderItemEvent("USB-C Hub", 2, new BigDecimal("49.90"), new BigDecimal("99.80")));
    }

    private static String fixture(String name) {
        try (InputStream in = EventContractTest.class.getResourceAsStream("/contracts/" + name)) {
            if (in == null) throw new IllegalStateException("Missing fixture: contracts/" + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // Removes whitespace outside string literals, keeping number text as written
    private static String compact(String json) {
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\') out.append(json.charAt(++i));
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
                out.append(c);
            } else if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
