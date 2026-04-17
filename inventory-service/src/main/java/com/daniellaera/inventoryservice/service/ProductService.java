package com.daniellaera.inventoryservice.service;

import com.daniellaera.inventoryservice.dto.ProductDTO;
import com.daniellaera.inventoryservice.dto.ProductRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ProductService {
    ProductDTO createProduct(ProductRequest request);
    List<ProductDTO> getAllProducts();
    ProductDTO getProductById(Long id);
}