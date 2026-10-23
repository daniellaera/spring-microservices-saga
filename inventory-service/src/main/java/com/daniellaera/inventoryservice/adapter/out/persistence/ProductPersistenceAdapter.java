package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ProductLockTimeoutException;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Opens no transaction of its own: inside a caller's transaction it works on the managed entities, otherwise
 * every call runs in Spring Data's own per-call transaction. The ForUpdate finders lock the row
 * (PESSIMISTIC_WRITE) for the rest of the caller's transaction, and give up after {@link #LOCK_TIMEOUT_MS} with a
 * ProductLockTimeoutException. The timeout is set for the whole transaction, so a later statement of the same
 * stock change waiting on another lock gives up after it too.
 */
@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepository {

    /** How long a stock change waits for a product another one has locked. */
    static final int LOCK_TIMEOUT_MS = 3000;

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
    public Optional<Product> findByIdForUpdate(long id) {
        try {
            productJpaRepository.setLocalLockTimeout(LOCK_TIMEOUT_MS + "ms");
            return productJpaRepository.findForUpdateById(id).map(ProductJpaMapper::toDomain);
        } catch (PessimisticLockingFailureException e) {
            throw new ProductLockTimeoutException("#" + id, e);
        }
    }

    @Override
    public Optional<Product> findByNameForUpdate(String name) {
        try {
            productJpaRepository.setLocalLockTimeout(LOCK_TIMEOUT_MS + "ms");
            return productJpaRepository.findForUpdateByName(name).map(ProductJpaMapper::toDomain);
        } catch (PessimisticLockingFailureException e) {
            throw new ProductLockTimeoutException(name, e);
        }
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
