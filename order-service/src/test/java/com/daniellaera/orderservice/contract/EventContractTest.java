package com.daniellaera.orderservice.contract;

import com.daniellaera.orderservice.audit.AuditPublisher;
import com.daniellaera.orderservice.dto.OrderEvent;
import com.daniellaera.orderservice.dto.OrderItemEvent;
import com.daniellaera.orderservice.dto.OrderStatusUpdate;
import com.daniellaera.orderservice.dto.PaymentEvent;
import com.daniellaera.orderservice.producer.OrderProducer;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Characterizes the JSON wire contracts of order-service against the fixtures in
 * src/test/resources/contracts, which were generated from the code as it is before
 * the hexagonal refactor. The fixtures must not change during the refactor.
 * <p>
 * Uses the application's auto-configured ObjectMapper. Besides JSONAssert (strict),
 * produced JSON is compared as text so number scale (49.90 vs 49.9) and property
 * order are pinned too.
 */
@JsonTest
class EventContractTest {

    // Envelope timestamps come from LocalDateTime.now()
    private static final CustomComparator IGNORE_TIMESTAMP = new CustomComparator(JSONCompareMode.STRICT,
            new Customization("timestamp", (expected, actual) -> true));

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
    }

    @Test
    void orderEvent_multiItemWithLegacyFields_isSentToOrdersTopicWithoutKey() throws Exception {
        new OrderProducer(kafkaTemplate, objectMapper).sendOrderEvent(multiItemOrderEvent());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("orders-topic"), message.capture());
        verifyNoMoreInteractions(kafkaTemplate);

        String expected = fixture("order-event.json");
        JSONAssert.assertEquals(expected, message.getValue(), JSONCompareMode.STRICT);
        assertThat(message.getValue()).isEqualTo(compact(expected));
    }

    @Test
    void orderEvent_outboxPayload_readsBackToTheSameEvent() {
        // The outbox relay deserializes the stored payload before sending it
        OrderEvent read = objectMapper.readValue(fixture("order-event.json"), OrderEvent.class);

        assertThat(read).isEqualTo(multiItemOrderEvent());
    }

    @Test
    void paymentEvent_multiItem_isReadWithoutItems() {
        // payment-service sends items, but order-service's PaymentEvent has no items component today
        PaymentEvent event = objectMapper.readValue(fixture("payment-event-multi-item.json"), PaymentEvent.class);

        assertThat(event).isEqualTo(new PaymentEvent(42L, "MacBook Pro", 1, "SUCCESS",
                new BigDecimal("1299.99"), new BigDecimal("1399.79"), "user@test.com"));
    }

    @Test
    void paymentEvent_legacySingleItem_isRead() {
        PaymentEvent event = objectMapper.readValue(fixture("payment-event-legacy-single-item.json"), PaymentEvent.class);

        assertThat(event).isEqualTo(new PaymentEvent(7L, "Mechanical Keyboard", 2, "FAILED",
                new BigDecimal("89.50"), new BigDecimal("179.00"), "buyer@test.com"));
    }

    @Test
    void orderStatusUpdate_serialization() throws Exception {
        String json = objectMapper.writeValueAsString(new OrderStatusUpdate(42L, "CONFIRMED", "user@test.com"));

        String expected = fixture("order-status-update.json");
        JSONAssert.assertEquals(expected, json, JSONCompareMode.STRICT);
        assertThat(json).isEqualTo(compact(expected));
    }

    @Test
    void auditOrderCreated_payloadIsTheOrderEvent() throws Exception {
        new AuditPublisher(kafkaTemplate, objectMapper)
                .publish("ORDER_CREATED", "user@test.com", "ORDER", "42", multiItemOrderEvent());

        String message = sentAuditMessage();
        JSONAssert.assertEquals(fixture("audit-order-created.json"), message, IGNORE_TIMESTAMP);
        assertThat(payloadOf(message)).isEqualTo(compact(fixture("order-event.json")));
    }

    @Test
    void auditOrderConfirmed_payloadIsThePaymentEventAsRead() throws Exception {
        PaymentEvent event = objectMapper.readValue(fixture("payment-event-multi-item.json"), PaymentEvent.class);

        new AuditPublisher(kafkaTemplate, objectMapper)
                .publish("ORDER_CONFIRMED", "user@test.com", "ORDER", "42", event);

        String message = sentAuditMessage();
        JSONAssert.assertEquals(fixture("audit-order-confirmed.json"), message, IGNORE_TIMESTAMP);
        assertThat(payloadOf(message)).isEqualTo(
                "{\"orderId\":42,\"productName\":\"MacBook Pro\",\"quantity\":1,\"status\":\"SUCCESS\","
                        + "\"price\":1299.99,\"totalAmount\":1399.79,\"userEmail\":\"user@test.com\"}");
    }

    private String sentAuditMessage() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("audit-topic"), message.capture());
        verifyNoMoreInteractions(kafkaTemplate);
        String sent = message.getValue();
        assertThat(objectMapper.readValue(sent, Map.class)).containsKey("timestamp");
        return sent;
    }

    private String payloadOf(String auditMessage) {
        return (String) objectMapper.readValue(auditMessage, Map.class).get("payload");
    }

    private static OrderEvent multiItemOrderEvent() {
        return new OrderEvent(42L, "MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1399.79"),
                "user@test.com", "pi_3QxYz123", List.of(
                        new OrderItemEvent("MacBook Pro", 1, new BigDecimal("1299.99"), new BigDecimal("1299.99")),
                        new OrderItemEvent("USB-C Hub", 2, new BigDecimal("49.90"), new BigDecimal("99.80"))));
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
