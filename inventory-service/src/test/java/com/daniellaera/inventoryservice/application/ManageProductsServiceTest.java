package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.application.fakes.InMemoryProductRepository;
import com.daniellaera.inventoryservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ProductAlreadyExistsException;
import com.daniellaera.inventoryservice.domain.model.ProductNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ports the create and restock scenarios of the former ProductServiceImplTest. */
class ManageProductsServiceTest {

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail();
    private final ManageProductsService service = new ManageProductsService(products, auditTrail);

    @Test
    void createProduct_savesAndAuditsTheSavedProduct() {
        Product created = service.createProduct("iPhone 16", 5, new BigDecimal("999.99"));

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.name()).isEqualTo("iPhone 16");
        assertThat(created.quantity()).isEqualTo(5);
        assertThat(created.price()).isEqualByComparingTo("999.99");
        assertThat(auditTrail.audits()).singleElement().satisfies(audit -> {
            assertThat(audit.eventType()).isEqualTo("PRODUCT_CREATED");
            assertThat(audit.userEmail()).isNull();
            assertThat(audit.product().id()).isEqualTo(created.id());
        });
    }

    @Test
    void createProduct_withoutPrice_storesZero() {
        assertThat(service.createProduct("Freebie", 1, null).price()).isEqualByComparingTo("0");
    }

    @Test
    void createProduct_existingName_throwsAndSavesNothing() {
        products.given("iPhone 16", 1, "999.99");

        assertThatThrownBy(() -> service.createProduct("iPhone 16", 5, new BigDecimal("999.99")))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessage("Product already exists: iPhone 16");
        assertThat(products.saved()).isEmpty();
        assertThat(auditTrail.audits()).isEmpty();
    }

    @Test
    void restock_addsQuantityAndAuditsTheSavedProduct() {
        Product existing = products.given("MacBook Pro", 10, "1299.99");

        Product restocked = service.restock(existing.id(), 15);

        assertThat(restocked.quantity()).isEqualTo(25);
        assertThat(restocked.name()).isEqualTo("MacBook Pro");
        assertThat(products.quantityOf("MacBook Pro")).isEqualTo(25);
        assertThat(products.locked()).containsExactly("#" + existing.id());
        assertThat(auditTrail.audits()).singleElement().satisfies(audit -> {
            assertThat(audit.eventType()).isEqualTo("PRODUCT_RESTOCKED");
            assertThat(audit.product().quantity()).isEqualTo(25);
        });
    }

    @Test
    void restock_unknownProduct_throwsWithTheRestockMessage() {
        assertThatThrownBy(() -> service.restock(999L, 10))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: 999");
        assertThat(auditTrail.audits()).isEmpty();
    }
}
