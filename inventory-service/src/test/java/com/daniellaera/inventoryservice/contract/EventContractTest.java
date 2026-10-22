package com.daniellaera.inventoryservice.contract;

import com.daniellaera.inventoryservice.NoCachingConfiguration;
import com.daniellaera.inventoryservice.audit.AuditPublisher;
import com.daniellaera.inventoryservice.consumer.InventoryConsumer;
import com.daniellaera.inventoryservice.dto.OrderEvent;
import com.daniellaera.inventoryservice.dto.OrderItemEvent;
import com.daniellaera.inventoryservice.dto.PaymentEvent;
import com.daniellaera.inventoryservice.dto.ProductDTO;
import com.daniellaera.inventoryservice.dto.ProductRequest;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.CompensationLogJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaRepository;
import com.daniellaera.inventoryservice.service.ProductServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private ProductJpaRepository productRepository;
    private StockReservationJpaRepository stockReservationRepository;
    private CompensationLogJpaRepository compensationLogRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        productRepository = mock(ProductJpaRepository.class);
        stockReservationRepository = mock(StockReservationJpaRepository.class);
        compensationLogRepository = mock(CompensationLogJpaRepository.class);
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
        stock(macbook(10), usbHub(50));

        consumer(mock(AuditPublisher.class)).consumeOrder(fixture("order-event-multi-item.json"));

        assertInventoryResult("inventory-result-approved-multi-item.json");
    }

    @Test
    void inventoryResult_rejectedLegacySingleItem_carriesTheResolvedItemAndQuantityZero() throws Exception {
        stock(keyboard(1));

        consumer(mock(AuditPublisher.class)).consumeOrder(fixture("order-event-legacy-single-item.json"));

        assertInventoryResult("inventory-result-rejected.json");
    }

    // ─── audit-topic ─────────────────────────────────────────────────────────

    @Test
    void auditStockDeducted_payloadIsTheOrderItem() throws Exception {
        stock(macbook(10), usbHub(50));

        consumer(auditPublisher()).consumeOrder(fixture("order-event-multi-item.json"));

        List<String> audits = sentAuditMessages(2);
        JSONAssert.assertEquals(fixture("audit-stock-deducted.json"), audits.get(0), IGNORE_TIMESTAMP);
        assertThat(payloadOf(audits.get(0))).isEqualTo(
                "{\"productName\":\"MacBook Pro\",\"quantity\":1,\"price\":1299.99,\"totalAmount\":1299.99}");
        assertThat(payloadOf(audits.get(1))).isEqualTo(
                "{\"productName\":\"USB-C Hub\",\"quantity\":2,\"price\":49.90,\"totalAmount\":99.80}");
    }

    @Test
    void auditStockRestored_payloadIsTheReservation() throws Exception {
        stock(keyboard(1));
        when(stockReservationRepository.findByOrderId(7L)).thenReturn(List.of(keyboardReservation()));

        consumer(auditPublisher()).handlePaymentResult(fixture("payment-event-legacy-single-item.json"));

        String audit = sentAuditMessages(1).get(0);
        JSONAssert.assertEquals(fixture("audit-stock-restored.json"), audit, IGNORE_TIMESTAMP);
        assertThat(payloadOf(audit)).isEqualTo(compact(payloadIn("audit-stock-restored.json")));
    }

    @Test
    void auditProductCreated_payloadIsTheSavedProduct() throws Exception {
        when(productRepository.save(any(ProductJpaEntity.class))).thenAnswer(inv -> persisted(inv.getArgument(0), 12L));

        new ProductServiceImpl(productRepository, auditPublisher())
                .createProduct(new ProductRequest("USB-C Hub", 50, new BigDecimal("49.90")));

        String audit = sentAuditMessages(1).get(0);
        JSONAssert.assertEquals(fixture("audit-product-created.json"), audit, IGNORE_TIMESTAMP);
        assertThat(payloadOf(audit)).isEqualTo(compact(payloadIn("audit-product-created.json")));
    }

    @Test
    void auditProductRestocked_payloadIsTheSavedProduct() throws Exception {
        ProductJpaEntity macbook = macbook(10);
        when(productRepository.findById(11L)).thenReturn(Optional.of(macbook));
        when(productRepository.save(macbook)).thenReturn(macbook);

        new ProductServiceImpl(productRepository, auditPublisher()).restock(11L, 5);

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

    private InventoryConsumer consumer(AuditPublisher auditPublisher) {
        return new InventoryConsumer(productRepository, compensationLogRepository, stockReservationRepository,
                kafkaTemplate, objectMapper, auditPublisher, inlineTransactionTemplate());
    }

    // Not proxied, so @Async does not apply and the message is sent synchronously
    private AuditPublisher auditPublisher() {
        return new AuditPublisher(kafkaTemplate, objectMapper);
    }

    // Runs the callback with synchronization active and fires afterCommit when it returns normally
    private static TransactionTemplate inlineTransactionTemplate() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            TransactionSynchronizationManager.initSynchronization();
            try {
                Object result = callback.doInTransaction(new SimpleTransactionStatus());
                TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
                return result;
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        });
        return template;
    }

    private void assertInventoryResult(String fixtureName) throws Exception {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("inventory-topic"), message.capture());

        String expected = fixture(fixtureName);
        JSONAssert.assertEquals(expected, message.getValue(), JSONCompareMode.STRICT);
        assertThat(message.getValue()).isEqualTo(compact(expected));
    }

    private List<String> sentAuditMessages(int count) {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(count)).send(eq("audit-topic"), message.capture());
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

    private void stock(ProductJpaEntity... products) {
        for (ProductJpaEntity product : products) {
            when(productRepository.findByName(product.getName())).thenReturn(Optional.of(product));
        }
    }

    private static ProductJpaEntity macbook(int quantity) {
        return product(11L, "MacBook Pro", quantity, "1299.99");
    }

    private static ProductJpaEntity usbHub(int quantity) {
        return product(12L, "USB-C Hub", quantity, "49.90");
    }

    private static ProductJpaEntity keyboard(int quantity) {
        return product(13L, "Mechanical Keyboard", quantity, "89.50");
    }

    private static ProductJpaEntity product(Long id, String name, int quantity, String price) {
        ProductJpaEntity product = new ProductJpaEntity();
        product.setName(name);
        product.setQuantity(quantity);
        product.setPrice(new BigDecimal(price));
        return persisted(product, id);
    }

    // Sets what JPA would: the generated id and the @PrePersist createdAt
    private static ProductJpaEntity persisted(ProductJpaEntity product, Long id) {
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "createdAt", CREATED_AT);
        return product;
    }

    private static StockReservationJpaEntity keyboardReservation() {
        StockReservationJpaEntity reservation = new StockReservationJpaEntity();
        reservation.setId(UUID.fromString("3f2b8c1e-5d4a-4e7b-9c1f-2a6d8e0b4c71"));
        reservation.setOrderId(7L);
        reservation.setProductName("Mechanical Keyboard");
        reservation.setQuantity(2);
        reservation.setReservedAt(LocalDateTime.of(2026, 10, 22, 10, 0, 5, 250000000));
        return reservation;
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
