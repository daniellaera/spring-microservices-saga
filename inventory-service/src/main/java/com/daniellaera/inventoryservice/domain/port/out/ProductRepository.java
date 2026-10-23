package com.daniellaera.inventoryservice.domain.port.out;

import com.daniellaera.inventoryservice.domain.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    /** A new product is inserted; for an existing one only the quantity is updated. */
    Product save(Product product);

    Optional<Product> findById(long id);

    Optional<Product> findByName(String name);

    /**
     * Like findById, but the product stays locked until the caller's transaction ends, so concurrent stock
     * changes wait for each other instead of overwriting each other. Call it inside a transaction. Waits a few
     * seconds at most for a lock another transaction holds, then throws ProductLockTimeoutException.
     */
    Optional<Product> findByIdForUpdate(long id);

    /**
     * Like findByName, with the same lock as findByIdForUpdate. A transaction that changes several products
     * locks them in name order, so two of them never wait on each other's locks.
     */
    Optional<Product> findByNameForUpdate(String name);

    boolean existsByName(String name);

    List<Product> findAll();
}
