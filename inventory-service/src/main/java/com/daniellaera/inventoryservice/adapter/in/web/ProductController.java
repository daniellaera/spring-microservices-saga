package com.daniellaera.inventoryservice.adapter.in.web;

import com.daniellaera.inventoryservice.adapter.in.web.dto.ProductDTO;
import com.daniellaera.inventoryservice.adapter.in.web.dto.ProductRequest;
import com.daniellaera.inventoryservice.adapter.in.web.dto.RestockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final CachedProductCatalog productCatalog;

    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productCatalog.createProduct(request));
    }

    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        return ResponseEntity.ok(productCatalog.getAllProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(productCatalog.getProductById(id));
    }

    @PutMapping("/{id}/restock")
    public ResponseEntity<ProductDTO> restock(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody RestockRequest request) {
        return ResponseEntity.ok(productCatalog.restock(id, request.quantity()));
    }
}