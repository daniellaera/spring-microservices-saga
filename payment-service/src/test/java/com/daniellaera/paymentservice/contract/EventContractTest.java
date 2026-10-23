package com.daniellaera.paymentservice.contract;

import com.daniellaera.paymentservice.adapter.out.messaging.KafkaAuditTrail;
import com.daniellaera.paymentservice.adapter.out.messaging.KafkaPaymentResultPublisher;
import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;
import com.daniellaera.paymentservice.domain.port.in.InventoryResult;
import com.daniellaera.paymentservice.adapter.in.messaging.InventoryResultEvent;
import com.daniellaera.paymentservice.adapter.in.messaging.InventoryResultEventListener;
import com.daniellaera.paymentservice.adapter.in.messaging.OrderItemEvent;
import com.daniellaera.paymentservice.adapter.in.web.dto.PaymentIntentResponse;
import com.daniellaera.paymentservice.adapter.in.web.dto.TransactionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Characterizes the JSON wire contracts of payment-service against the fixtures in
 * src/test/resources/contracts, which were generated from the code as it is before
 * the hexagonal refactor. The fixtures must not change during the refactor. The
 * inventory-result inputs are inventory-service's own output fixtures.
 * <p>
 * Uses the application's auto-configured ObjectMapper. Besides JSONAssert (strict),
 * produced JSON is compared as text so number scale (49.90 vs 49.9) and property
 * order are pinned too. The audit envelope is a Map.of, so only its payload is
 * compared as text.
 */
@JsonTest
class EventContractTest {

    // Envelope timestamps come from LocalDateTime.now()
    private static final CustomComparator IGNORE_TIMESTAMP = new CustomComparator(JSONCompareMode.STRICT,
            new Customization("timestamp", (expected, actual) -> true));

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 22, 10, 0, 7, 123456000);

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
    }

    // ─── inventory-topic (input) ─────────────────────────────────────────────

    @Test
    void inventoryResult_approvedMultiItemWithIntent_isRead() {
        InventoryResultEvent event = objectMapper.readValue(
                fixture("inventory-result-approved-multi-item-with-intent.json"), InventoryResultEvent.class);

        assertThat(event).isEqualTo(new InventoryResultEvent(42L, "APPROVED", "MacBook Pro", 1,
                new BigDecimal("1299.99"), new BigDecimal("1399.79"), "user@test.com", "pi_3QxYz123", multiItems()));
    }

    @Test
    void inventoryResult_approvedWithoutIntent_isRead() {
        InventoryResultEvent event = objectMapper.readValue(
                fixture("inventory-result-approved-without-intent.json"), InventoryResultEvent.class);

        assertThat(event).isEqualTo(new InventoryResultEvent(43L, "APPROVED", "MacBook Pro", 1,
                new BigDecimal("1299.99"), new BigDecimal("1399.79"), "user@test.com", null, multiItems()));
    }

    @Test
    void inventoryResult_rejected_isRead() {
        InventoryResultEvent event = objectMapper.readValue(fixture("inventory-result-rejected.json"),
                InventoryResultEvent.class);

        assertThat(event).isEqualTo(new InventoryResultEvent(7L, "REJECTED", "Mechanical Keyboard", 0,
                new BigDecimal("89.50"), new BigDecimal("179.00"), "buyer@test.com", "pi_legacy7", List.of(
                        new OrderItemEvent("Mechanical Keyboard", 2, new BigDecimal("89.50"), new BigDecimal("179.00")))));
    }

    // ─── payment-topic and PAYMENT_SUCCEEDED / PAYMENT_FAILED ────────────────

    @Test
    void paymentSucceeded_eventAndAudit() throws Exception {
        InventoryResult result = inventoryResult("inventory-result-approved-multi-item-with-intent.json");

        resultPublisher().publish(result, PaymentStatus.SUCCESS);
        auditTrail().paymentProcessed(
                // the paying intent is stored but is not part of the audit payload (fixture unchanged)
                new PaymentTransaction(7L, 42L, new BigDecimal("1399.79"), PaymentStatus.SUCCESS, "pi_3QxYz123",
                        CREATED_AT),
                result.userEmail());

        assertPaymentEvent("payment-event-success.json");
        assertAudit("audit-payment-succeeded.json");
    }

    @Test
    void paymentFailed_eventAndAudit() throws Exception {
        InventoryResult result = inventoryResult("inventory-result-rejected.json");

        resultPublisher().publish(result, PaymentStatus.FAILED);
        auditTrail().paymentProcessed(
                new PaymentTransaction(7L, 7L, new BigDecimal("179.00"), PaymentStatus.FAILED, null, CREATED_AT),
                result.userEmail());

        assertPaymentEvent("payment-event-failed.json");
        assertAudit("audit-payment-failed.json");
    }

    // ─── PAYMENT_INITIATED ───────────────────────────────────────────────────

    @Test
    void paymentInitiated_payloadIsTheRequest() throws Exception {
        auditTrail().paymentInitiated("pi_3QxYz123",
                new CreatePaymentIntentCommand(139979L, "chf", "MacBook Pro", "user@test.com"));

        assertAudit("audit-payment-initiated.json");
    }

    // ─── REST ────────────────────────────────────────────────────────────────

    @Test
    void transactionDtoList_serialization() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(
                new TransactionDTO(7L, 42L, new BigDecimal("1399.79"), PaymentStatus.SUCCESS),
                new TransactionDTO(8L, 7L, new BigDecimal("179.00"), PaymentStatus.FAILED)));

        assertJson("transaction-list.json", json);
    }

    @Test
    void paymentIntentResponse_serialization() throws Exception {
        String json = objectMapper.writeValueAsString(new PaymentIntentResponse("pi_3QxYz123_secret_abc",
                "pi_3QxYz123", "requires_payment_method", "pk_test_contract"));

        assertJson("payment-intent-response.json", json);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private KafkaPaymentResultPublisher resultPublisher() {
        return new KafkaPaymentResultPublisher(kafkaTemplate, objectMapper);
    }

    // Not proxied, so @Async does not apply and the message is sent synchronously
    private KafkaAuditTrail auditTrail() {
        return new KafkaAuditTrail(kafkaTemplate, objectMapper);
    }

    // What the listener hands to the use case for the inventory-topic fixture, so the adapter path is pinned
    private InventoryResult inventoryResult(String fixtureName) throws Exception {
        List<InventoryResult> results = new ArrayList<>();
        new InventoryResultEventListener(results::add, (orderId, userEmail, error) -> { }, objectMapper).handleInventoryResult(fixture(fixtureName));
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    private void assertPaymentEvent(String fixtureName) throws Exception {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("payment-topic"), message.capture());
        assertJson(fixtureName, message.getValue());
    }

    private void assertAudit(String fixtureName) throws Exception {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("audit-topic"), message.capture());
        String sent = message.getValue();
        assertThat(objectMapper.readValue(sent, Map.class)).containsKey("timestamp");
        String expected = fixture(fixtureName);
        JSONAssert.assertEquals(expected, sent, IGNORE_TIMESTAMP);
        assertThat(payloadOf(sent)).isEqualTo(compact(payloadOf(expected)));
    }

    private void assertJson(String fixtureName, String actual) throws Exception {
        String expected = fixture(fixtureName);
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
        assertThat(actual).isEqualTo(compact(expected));
    }

    private String payloadOf(String auditMessage) {
        return (String) objectMapper.readValue(auditMessage, Map.class).get("payload");
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
