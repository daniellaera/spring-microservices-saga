package com.daniellaera.inventoryservice.domain.port.out;

import com.daniellaera.inventoryservice.domain.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    /** A new product is inserted; for an existing one only the quantity is updated. */
    Product save(Product product);

    Optional<Product> findById(long id);

    Optional<Product> findByName(String name);

    boolean existsByName(String name);

    List<Product> findAll();
}
