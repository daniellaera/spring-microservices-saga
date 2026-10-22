package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.application.fakes.InMemoryProductRepository;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ProductNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ports the read scenarios of the former ProductServiceImplTest. */
class ProductQueryServiceTest {

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final ProductQueryService service = new ProductQueryService(products);

    @Test
    void getAllProducts_returnsEveryProduct() {
        products.given("MacBook Pro", 10, "1299.99");

        assertThat(service.getAllProducts()).singleElement().satisfies(product -> {
            assertThat(product.name()).isEqualTo("MacBook Pro");
            assertThat(product.quantity()).isEqualTo(10);
            assertThat(product.price()).isEqualByComparingTo("1299.99");
        });
    }

    @Test
    void getAllProducts_noProducts_isEmpty() {
        assertThat(service.getAllProducts()).isEmpty();
    }

    @Test
    void getProductById_returnsTheProduct() {
        Product existing = products.given("MacBook Pro", 10, "1299.99");

        Product found = service.getProductById(existing.id());

        assertThat(found.name()).isEqualTo("MacBook Pro");
        assertThat(found.quantity()).isEqualTo(10);
        assertThat(found.price()).isEqualByComparingTo("1299.99");
    }

    @Test
    void getProductById_unknownId_throwsWithTheGetMessage() {
        assertThatThrownBy(() -> service.getProductById(999L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found with id: 999");
    }
}
