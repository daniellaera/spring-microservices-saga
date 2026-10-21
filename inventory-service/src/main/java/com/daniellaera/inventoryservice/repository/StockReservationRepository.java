package com.daniellaera.inventoryservice.repository;

import com.daniellaera.inventoryservice.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    List<StockReservation> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
