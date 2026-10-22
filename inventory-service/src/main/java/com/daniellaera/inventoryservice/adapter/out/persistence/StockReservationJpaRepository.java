package com.daniellaera.inventoryservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockReservationJpaRepository extends JpaRepository<StockReservationJpaEntity, UUID> {
    List<StockReservationJpaEntity> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
