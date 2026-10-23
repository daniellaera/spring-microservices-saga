package com.daniellaera.inventoryservice.application.fakes;

import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Behaves like the persistence adapter: assigns ids and createdAt to new products, only updates the quantity of
 * existing ones and hands out copies, never the stored instances (so a missing save is visible). The ForUpdate
 * finders take no lock, but record what was locked and in which order.
 */
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> products = new LinkedHashMap<>();
    private final List<Product> saved = new ArrayList<>();
    private final List<String> locked = new ArrayList<>();
    private long nextId = 1;
    private LocalDateTime clock = LocalDateTime.of(2025, 1, 1, 9, 0);

    /** Stores a product directly, as if it had been created earlier. */
    public Product given(String name, int quantity, String price) {
        clock = clock.plusMinutes(1);
        Product stored = Product.restore(nextId++, name, quantity, new BigDecimal(price), clock);
        products.put(stored.id(), stored);
        return copy(stored);
    }

    @Override
    public Product save(Product product) {
        Product stored;
        if (product.id() == null) {
            clock = clock.plusMinutes(1);
            stored = Product.restore(nextId++, product.name(), product.quantity(), product.price(), clock);
        } else {
            Product existing = products.get(product.id());
            if (existing == null) {
                throw new IllegalStateException("Product " + product.id() + " no longer exists");
            }
            stored = Product.restore(existing.id(), existing.name(), product.quantity(), existing.price(),
                    existing.createdAt());
        }
        products.put(stored.id(), stored);
        saved.add(copy(stored));
        return copy(stored);
    }

    @Override
    public Optional<Product> findById(long id) {
        return Optional.ofNullable(products.get(id)).map(InMemoryProductRepository::copy);
    }

    @Override
    public Optional<Product> findByName(String name) {
        return products.values().stream().filter(p -> p.name().equals(name)).findFirst()
                .map(InMemoryProductRepository::copy);
    }

    @Override
    public Optional<Product> findByIdForUpdate(long id) {
        locked.add("#" + id);
        return findById(id);
    }

    @Override
    public Optional<Product> findByNameForUpdate(String name) {
        locked.add(name);
        return findByName(name);
    }

    @Override
    public boolean existsByName(String name) {
        return products.values().stream().anyMatch(p -> p.name().equals(name));
    }

    @Override
    public List<Product> findAll() {
        return products.values().stream().map(InMemoryProductRepository::copy).toList();
    }

    public void remove(String name) {
        products.values().removeIf(p -> p.name().equals(name));
    }

    public int quantityOf(String name) {
        return findByName(name).orElseThrow().quantity();
    }

    /** Every lock taken, in order: a product name, or "#id" when locked by id. */
    public List<String> locked() {
        return List.copyOf(locked);
    }

    /** Every product as stored by each save call, in call order. */
    public List<Product> saved() {
        return List.copyOf(saved);
    }

    private static Product copy(Product p) {
        return Product.restore(p.id(), p.name(), p.quantity(), p.price(), p.createdAt());
    }
}
