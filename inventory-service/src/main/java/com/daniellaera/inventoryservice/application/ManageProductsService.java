package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ProductAlreadyExistsException;
import com.daniellaera.inventoryservice.domain.model.ProductNotFoundException;
import com.daniellaera.inventoryservice.domain.port.in.ManageProductsUseCase;
import com.daniellaera.inventoryservice.domain.port.out.AuditTrail;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * createProduct is not transactional: each repository call runs in its own transaction. restock is, so the
 * product stays locked from read to write and a concurrent order or compensation cannot overwrite it.
 */
public class ManageProductsService implements ManageProductsUseCase {

    private final ProductRepository productRepository;
    private final AuditTrail auditTrail;

    public ManageProductsService(ProductRepository productRepository, AuditTrail auditTrail) {
        this.productRepository = productRepository;
        this.auditTrail = auditTrail;
    }

    @Override
    public Product createProduct(String name, int quantity, BigDecimal price) {
        if (productRepository.existsByName(name)) {
            throw new ProductAlreadyExistsException(name);
        }
        Product saved = productRepository.save(Product.create(name, quantity, price != null ? price : BigDecimal.ZERO));
        auditTrail.productCreated(saved);
        return saved;
    }

    @Override
    @Transactional
    public Product restock(long id, int quantity) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ProductNotFoundException.forRestock(id));
        product.restock(quantity);
        Product saved = productRepository.save(product);
        auditTrail.productRestocked(saved);
        return saved;
    }
}
