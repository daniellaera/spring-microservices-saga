package com.daniellaera.orderservice.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {
    List<OrderJpaEntity> findByUserEmail(String userEmail);
    Page<OrderJpaEntity> findByUserEmail(String userEmail, Pageable pageable);
}
