package com.daniellaera.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The messages are part of the HTTP contract (404 / 409 bodies) and of the logs. */
class DomainExceptionsTest {

    @Test
    void productNotFound_byIdAndForRestock_haveDifferentMessages() {
        assertThat(ProductNotFoundException.withId(99L)).hasMessage("Product not found with id: 99");
        assertThat(ProductNotFoundException.forRestock(99L)).hasMessage("Product not found: 99");
    }

    @Test
    void productAlreadyExists_namesTheProduct() {
        assertThat(new ProductAlreadyExistsException("iPhone 16")).hasMessage("Product already exists: iPhone 16");
    }

    @Test
    void unknownProduct_namesTheProduct() {
        assertThat(new UnknownProductException("Unknown Product")).hasMessage("Product not found: Unknown Product");
    }
}
