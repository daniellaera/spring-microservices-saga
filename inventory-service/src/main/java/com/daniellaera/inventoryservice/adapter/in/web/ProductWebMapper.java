package com.daniellaera.inventoryservice.adapter.in.web;

import com.daniellaera.inventoryservice.adapter.in.web.dto.ProductDTO;
import com.daniellaera.inventoryservice.domain.model.Product;

public final class ProductWebMapper {

    private ProductWebMapper() {
    }

    public static ProductDTO toDto(Product p) {
        return new ProductDTO(p.id(), p.name(), p.quantity(), p.price(), p.createdAt());
    }
}
