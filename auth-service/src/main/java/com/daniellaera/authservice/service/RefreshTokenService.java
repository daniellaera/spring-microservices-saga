package com.daniellaera.authservice.service;

import com.daniellaera.authservice.audit.AuditPublisher;
import com.daniellaera.authservice.adapter.out.persistence.RefreshTokenJpaEntity;
import com.daniellaera.authservice.adapter.out.persistence.UserJpaEntity;
import com.daniellaera.authservice.adapter.out.persistence.RefreshTokenJpaRepository;
import com.daniellaera.authservice.adapter.out.persistence.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final UserJpaRepository userRepository;
    private final AuditPublisher auditPublisher;

    @Value("${app.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Transactional
    public RefreshTokenJpaEntity createRefreshToken(String userEmail) {
        UserJpaEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        refreshTokenRepository.revokeAllUserTokens(user);

        RefreshTokenJpaEntity refreshToken = new RefreshTokenJpaEntity();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshExpiration));
        refreshToken.setRevoked(false);

        RefreshTokenJpaEntity saved = refreshTokenRepository.save(refreshToken);
        log.info("=== RefreshToken created for {}", userEmail);
        auditPublisher.publish("TOKEN_REFRESHED", userEmail, "USER", userEmail, userEmail);
        return saved;
    }

    @Transactional
    public RefreshTokenJpaEntity validateRefreshToken(String token) {
        RefreshTokenJpaEntity refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (refreshToken.isRevoked()) {
            throw new RuntimeException("Refresh token revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new RuntimeException("Refresh token expired");
        }

        // open-in-view is disabled, so the lazy user association must be
        // initialized here while the persistence context is still open —
        // otherwise callers hit a LazyInitializationException after this
        // method returns.
        Hibernate.initialize(refreshToken.getUser());

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                    log.info("=== RefreshToken revoked for {}", rt.getUser().getEmail());
                });
    }
}
