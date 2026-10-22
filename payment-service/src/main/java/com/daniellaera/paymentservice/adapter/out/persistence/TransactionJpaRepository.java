package com.daniellaera.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, Long> {

    Optional<TransactionJpaEntity> findByOrderId(Long orderId);

    Optional<TransactionJpaEntity> findByPaymentIntentId(String paymentIntentId);
}
