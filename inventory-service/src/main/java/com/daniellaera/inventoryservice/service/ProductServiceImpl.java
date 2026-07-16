package com.daniellaera.inventoryservice.service;

import com.daniellaera.inventoryservice.audit.AuditPublisher;
import com.daniellaera.inventoryservice.dto.ProductDTO;
import com.daniellaera.inventoryservice.dto.ProductRequest;
import com.daniellaera.inventoryservice.exception.ResourceAlreadyExistsException;
import com.daniellaera.inventoryservice.exception.ResourceNotFoundException;
import com.daniellaera.inventoryservice.model.Product;
import com.daniellaera.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final AuditPublisher auditPublisher;

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductDTO createProduct(ProductRequest request) {
        if (productRepository.existsByName(request.name())) {
            throw new ResourceAlreadyExistsException("Product already exists: " + request.name());
        }
        Product product = new Product();
        product.setName(request.name());
        product.setQuantity(request.quantity());
        product.setPrice(request.price() != null ? request.price() : java.math.BigDecimal.ZERO);
        Product saved = productRepository.save(product);
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
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return toDTO(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductDTO restock(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setQuantity(product.getQuantity() + quantity);
        Product saved = productRepository.save(product);
        auditPublisher.publish("PRODUCT_RESTOCKED", null, "PRODUCT", String.valueOf(saved.getId()), saved);
        return toDTO(saved);
    }

    private ProductDTO toDTO(Product p) {
        return new ProductDTO(p.getId(), p.getName(), p.getQuantity(), p.getPrice(), p.getCreatedAt());
    }
}