package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.domain.model.StockReservation;

public final class StockReservationJpaMapper {

    private StockReservationJpaMapper() {
    }

    public static StockReservation toDomain(StockReservationJpaEntity entity) {
        return new StockReservation(entity.getId(), entity.getOrderId(), entity.getProductName(),
                entity.getQuantity(), entity.getReservedAt());
    }

    /** Maps a reservation that has never been persisted. Without a reservedAt, the entity's default (now) applies. */
    public static StockReservationJpaEntity toNewEntity(StockReservation reservation) {
        StockReservationJpaEntity entity = new StockReservationJpaEntity();
        entity.setOrderId(reservation.orderId());
        entity.setProductName(reservation.productName());
        entity.setQuantity(reservation.quantity());
        if (reservation.reservedAt() != null) {
            entity.setReservedAt(reservation.reservedAt());
        }
        return entity;
    }
}
