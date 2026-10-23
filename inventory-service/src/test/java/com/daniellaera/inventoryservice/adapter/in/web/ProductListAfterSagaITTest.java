package com.daniellaera.inventoryservice.adapter.in.web;

import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import com.daniellaera.inventoryservice.adapter.in.messaging.OrderEvent;
import com.daniellaera.inventoryservice.adapter.in.messaging.OrderEventListener;
import com.daniellaera.inventoryservice.adapter.in.messaging.PaymentEventListener;
import com.daniellaera.inventoryservice.adapter.in.web.dto.ProductDTO;
import com.daniellaera.inventoryservice.adapter.out.persistence.CompensationLogJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With a real cache in front of the product list: the list a customer reads right after an order or a
 * compensation shows the stock it left, not the stock cached before it.
 */
@SpringBootTest(properties = "spring.cache.type=simple")
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductListAfterSagaITTest {

    @Autowired
    private CachedProductCatalog catalog;

    @Autowired
    private OrderEventListener orderEventListener;

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private StockReservationJpaRepository stockReservationRepository;

    @Autowired
    private CompensationLogJpaRepository compensationLogRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        compensationLogRepository.deleteAll();
        stockReservationRepository.deleteAll();
        productRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        ProductJpaEntity lamp = new ProductJpaEntity();
        lamp.setName("Lamp");
        lamp.setQuantity(10);
        lamp.setPrice(new BigDecimal("25.00"));
        productRepository.save(lamp);
    }

    @Test
    void listReflectsTheStockLeftByAnOrderAndByItsCompensation() throws Exception {
        assertThat(lampInTheList()).isEqualTo(10);

        orderEventListener.consumeOrder(objectMapper.writeValueAsString(new OrderEvent(700L, "Lamp", 2,
                new BigDecimal("25.00"), new BigDecimal("50.00"), "user@test.com", "pi_700", null)));

        assertThat(lampInTheList()).isEqualTo(8);

        paymentEventListener.handlePaymentResult("{\"orderId\":700,\"status\":\"FAILED\",\"userEmail\":\"user@test.com\"}");

        assertThat(lampInTheList()).isEqualTo(10);
    }

    @Test
    void rejectedOrder_keepsTheCachedList() throws Exception {
        assertThat(lampInTheList()).isEqualTo(10);
        // the stock changes behind the cache's back, and a rejected order must not refresh it
        ProductJpaEntity lamp = productRepository.findByName("Lamp").orElseThrow();
        lamp.setQuantity(1);
        productRepository.save(lamp);

        orderEventListener.consumeOrder(objectMapper.writeValueAsString(new OrderEvent(701L, "Lamp", 2,
                new BigDecimal("25.00"), new BigDecimal("50.00"), "user@test.com", "pi_701", null)));

        assertThat(lampInTheList()).isEqualTo(10);
    }

    private int lampInTheList() {
        return catalog.getAllProducts().stream()
                .filter(product -> product.name().equals("Lamp"))
                .map(ProductDTO::quantity)
                .findFirst()
                .orElseThrow();
    }
}
