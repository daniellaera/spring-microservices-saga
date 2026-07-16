package com.daniellaera.authservice.service;

import com.daniellaera.authservice.audit.AuditPublisher;
import com.daniellaera.authservice.model.RefreshToken;
import com.daniellaera.authservice.model.User;
import com.daniellaera.authservice.repository.RefreshTokenRepository;
import com.daniellaera.authservice.repository.UserRepository;
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

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuditPublisher auditPublisher;

    @Value("${app.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Transactional
    public RefreshToken createRefreshToken(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        refreshTokenRepository.revokeAllUserTokens(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshExpiration));
        refreshToken.setRevoked(false);

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("=== RefreshToken created for {}", userEmail);
        auditPublisher.publish("TOKEN_REFRESHED", userEmail, "USER", userEmail, userEmail);
        return saved;
    }

    @Transactional
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
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
