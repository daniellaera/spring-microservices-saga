package com.daniellaera.inventoryservice.repository;

import com.daniellaera.inventoryservice.model.CompensationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CompensationLogRepository extends JpaRepository<CompensationLog, UUID> {
    boolean existsByOrderId(Long orderId);
}
