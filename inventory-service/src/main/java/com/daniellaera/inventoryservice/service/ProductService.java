package com.daniellaera.inventoryservice.service;

import com.daniellaera.inventoryservice.dto.ProductRequest;
import com.daniellaera.inventoryservice.model.Product;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ProductService {
    Product createProduct(ProductRequest request);
    List<Product> getAllProducts();
}