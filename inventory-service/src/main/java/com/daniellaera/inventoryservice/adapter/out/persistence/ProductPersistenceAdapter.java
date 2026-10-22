package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Opens no transaction of its own: inside a caller's transaction it works on the managed entities, otherwise
 * every call runs in Spring Data's own per-call transaction, as the service did before. No @Version and no
 * lock, so concurrent updates still race (last write wins).
 */
@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        if (product.id() == null) {
            return ProductJpaMapper.toDomain(productJpaRepository.save(ProductJpaMapper.toNewEntity(product)));
        }
        // Existing products only ever change quantity: update the loaded entity, never rebuild it
        ProductJpaEntity entity = productJpaRepository.findById(product.id())
                .orElseThrow(() -> new IllegalStateException("Product " + product.id() + " no longer exists"));
        entity.setQuantity(product.quantity());
        return ProductJpaMapper.toDomain(productJpaRepository.save(entity));
    }

    @Override
    public Optional<Product> findById(long id) {
        return productJpaRepository.findById(id).map(ProductJpaMapper::toDomain);
    }

    @Override
    public Optional<Product> findByName(String name) {
        return productJpaRepository.findByName(name).map(ProductJpaMapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return productJpaRepository.existsByName(name);
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAll().stream().map(ProductJpaMapper::toDomain).toList();
    }
}
