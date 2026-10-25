package com.daniellaera.authservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshTokenJpaEntity rt WHERE rt.user = :user")
    void deleteByUser(@Param("user") UserJpaEntity user);

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity rt SET rt.revoked = true WHERE rt.user = :user")
    void revokeAllUserTokens(@Param("user") UserJpaEntity user);
}
