package com.daniellaera.inventoryservice.domain.port.in;

import com.daniellaera.inventoryservice.domain.model.Product;

import java.util.List;

public interface ProductQuery {
    List<Product> getAllProducts();

    Product getProductById(long id);
}
