package com.daniellaera.inventoryservice.adapter.in.web;

import com.daniellaera.inventoryservice.adapter.in.web.dto.ProductDTO;
import com.daniellaera.inventoryservice.adapter.in.web.dto.ProductRequest;
import com.daniellaera.inventoryservice.domain.port.in.ManageProductsUseCase;
import com.daniellaera.inventoryservice.domain.port.in.ProductQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The REST side of the product use cases, with the product list cache. Only create and restock evict it:
 * stock changes made by orders and compensation leave it stale until the TTL expires (current behaviour).
 */
@Component
@RequiredArgsConstructor
public class CachedProductCatalog {

    // Was "products". Redis values are JDK-serialized, which embeds the class name, and ProductDTO moved from
    // ...inventoryservice.dto to ...adapter.in.web.dto: entries written before the move could not be read back
    // and GET /products would fail with a 500 until they expired. A new cache name skips them.
    static final String CACHE = "products-v2";

    private final ProductQuery productQuery;
    private final ManageProductsUseCase manageProductsUseCase;

    @CacheEvict(value = CACHE, allEntries = true)
    public ProductDTO createProduct(ProductRequest request) {
        return ProductWebMapper.toDto(
                manageProductsUseCase.createProduct(request.name(), request.quantity(), request.price()));
    }

    @Cacheable(value = CACHE, unless = "#result.isEmpty()")
    public List<ProductDTO> getAllProducts() {
        return productQuery.getAllProducts().stream().map(ProductWebMapper::toDto).toList();
    }

    public ProductDTO getProductById(long id) {
        return ProductWebMapper.toDto(productQuery.getProductById(id));
    }

    @CacheEvict(value = CACHE, allEntries = true)
    public ProductDTO restock(long id, int quantity) {
        return ProductWebMapper.toDto(manageProductsUseCase.restock(id, quantity));
    }
}
