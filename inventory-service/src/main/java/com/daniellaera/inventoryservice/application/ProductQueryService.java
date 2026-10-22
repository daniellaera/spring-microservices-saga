package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ProductNotFoundException;
import com.daniellaera.inventoryservice.domain.port.in.ProductQuery;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;

import java.util.List;

public class ProductQueryService implements ProductQuery {

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public Product getProductById(long id) {
        return productRepository.findById(id).orElseThrow(() -> ProductNotFoundException.withId(id));
    }
}
