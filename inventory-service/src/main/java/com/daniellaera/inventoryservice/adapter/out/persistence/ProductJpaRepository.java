package com.daniellaera.inventoryservice.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * PostgreSQL's {@code SET LOCAL lock_timeout}: until the current transaction ends, a statement waiting for a
     * lock fails after {@code timeout} (e.g. "3000ms") instead of waiting forever. The
     * {@code jakarta.persistence.lock.timeout} hint does not do this: Hibernate 7.2 applies it after the locking
     * query has run.
     */
    @Query(value = "select set_config('lock_timeout', :timeout, true)", nativeQuery = true)
    String setLocalLockTimeout(@Param("timeout") String timeout);

}
