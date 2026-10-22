package com.daniellaera.inventoryservice.service;

import com.daniellaera.inventoryservice.audit.AuditPublisher;
import com.daniellaera.inventoryservice.dto.ProductDTO;
import com.daniellaera.inventoryservice.dto.ProductRequest;
import com.daniellaera.inventoryservice.exception.ResourceAlreadyExistsException;
import com.daniellaera.inventoryservice.exception.ResourceNotFoundException;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductJpaRepository productRepository;
    private final AuditPublisher auditPublisher;

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductDTO createProduct(ProductRequest request) {
        if (productRepository.existsByName(request.name())) {
            throw new ResourceAlreadyExistsException("Product already exists: " + request.name());
        }
        ProductJpaEntity product = new ProductJpaEntity();
        product.setName(request.name());
        product.setQuantity(request.quantity());
        product.setPrice(request.price() != null ? request.price() : java.math.BigDecimal.ZERO);
        ProductJpaEntity saved = productRepository.save(product);
        auditPublisher.publish("PRODUCT_CREATED", null, "PRODUCT", String.valueOf(saved.getId()), saved);
        return toDTO(saved);
    }

    @Override
    @Cacheable(value = "products", unless = "#result.isEmpty()")
    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public ProductDTO getProductById(Long id) {
        ProductJpaEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return toDTO(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductDTO restock(Long id, Integer quantity) {
        ProductJpaEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setQuantity(product.getQuantity() + quantity);
        ProductJpaEntity saved = productRepository.save(product);
        auditPublisher.publish("PRODUCT_RESTOCKED", null, "PRODUCT", String.valueOf(saved.getId()), saved);
        return toDTO(saved);
    }

    private ProductDTO toDTO(ProductJpaEntity p) {
        return new ProductDTO(p.getId(), p.getName(), p.getQuantity(), p.getPrice(), p.getCreatedAt());
    }
}