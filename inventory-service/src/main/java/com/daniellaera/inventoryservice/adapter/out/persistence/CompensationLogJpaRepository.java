package com.daniellaera.inventoryservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CompensationLogJpaRepository extends JpaRepository<CompensationLogJpaEntity, UUID> {
    boolean existsByOrderId(Long orderId);
}
