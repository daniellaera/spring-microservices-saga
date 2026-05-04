package com.daniellaera.inventoryservice.service;

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

    @Override
    public ProductDTO createProduct(ProductRequest request) {
        if (productRepository.existsByName(request.name())) {
            throw new ResourceAlreadyExistsException("Product already exists: " + request.name());
        }
        Product product = new Product();
        product.setName(request.name());
        product.setQuantity(request.quantity());
        Product saved = productRepository.save(product);
        return new ProductDTO(saved.getId(), saved.getName(), saved.getQuantity(), saved.getCreatedAt());
    }

    @Override
    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(p -> new ProductDTO(p.getId(), p.getName(), p.getQuantity(), p.getCreatedAt()))
                .toList();
    }

    @Override
    public ProductDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return new ProductDTO(product.getId(), product.getName(), product.getQuantity(), product.getCreatedAt());
    }
}