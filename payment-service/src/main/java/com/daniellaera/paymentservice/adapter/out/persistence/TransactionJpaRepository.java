package com.daniellaera.paymentservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, Long> {}
