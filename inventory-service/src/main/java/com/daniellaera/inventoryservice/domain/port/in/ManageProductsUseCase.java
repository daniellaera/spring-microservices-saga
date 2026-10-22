package com.daniellaera.inventoryservice.domain.port.in;

import com.daniellaera.inventoryservice.domain.model.Product;

import java.math.BigDecimal;

public interface ManageProductsUseCase {
    Product createProduct(String name, int quantity, BigDecimal price);

    Product restock(long id, int quantity);
}
