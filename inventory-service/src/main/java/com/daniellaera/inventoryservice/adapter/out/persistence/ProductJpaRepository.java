package com.daniellaera.inventoryservice.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {
    Optional<ProductJpaEntity> findByName(String name);

    boolean existsByName(String name);

    // SELECT ... FOR UPDATE: the row stays locked until the transaction ends
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductJpaEntity> findForUpdateById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductJpaEntity> findForUpdateByName(String name);

}
