package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.domain.model.Product;

public final class ProductJpaMapper {

    private ProductJpaMapper() {
    }

    public static Product toDomain(ProductJpaEntity entity) {
        return Product.restore(entity.getId(), entity.getName(), entity.getQuantity(), entity.getPrice(),
                entity.getCreatedAt());
    }

    /** Maps a product that has never been persisted; id and createdAt are assigned on insert. */
    public static ProductJpaEntity toNewEntity(Product product) {
        ProductJpaEntity entity = new ProductJpaEntity();
        entity.setName(product.name());
        entity.setQuantity(product.quantity());
        entity.setPrice(product.price());
        return entity;
    }
}
