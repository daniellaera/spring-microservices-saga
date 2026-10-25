package com.daniellaera.authservice.application;

import com.daniellaera.authservice.domain.model.InvalidRefreshTokenException;
import com.daniellaera.authservice.domain.model.RefreshToken;
import com.daniellaera.authservice.domain.model.User;
import com.daniellaera.authservice.domain.model.UserNotFoundException;
import com.daniellaera.authservice.domain.port.out.AuditTrail;
import com.daniellaera.authservice.domain.port.out.RefreshTokenRepository;
import com.daniellaera.authservice.domain.port.out.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh tokens: one transaction per operation, called through the bean (never from inside this class). An expired
 * token's revocation is rolled back together with the error it throws, as it always has been.
 */
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuditTrail auditTrail;
    private final Duration validity;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                               AuditTrail auditTrail, Duration validity) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.auditTrail = auditTrail;
        this.validity = validity;
    }

    // the user's other refresh tokens are revoked: one valid token per user
    @Transactional
    public RefreshToken createRefreshToken(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userEmail));

        refreshTokenRepository.revokeAllOf(user.email());

        RefreshToken saved = refreshTokenRepository.save(RefreshToken.issue(UUID.randomUUID().toString(),
                user.email(), user.role(), Instant.now().plus(validity)));
        log.info("=== RefreshToken created for {}", userEmail);
        auditTrail.refreshTokenIssued(userEmail);
        return saved;
    }

    @Transactional
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (refreshToken.revoked()) {
            throw new InvalidRefreshTokenException("Refresh token revoked");
        }

        if (refreshToken.isExpired(Instant.now())) {
            refreshTokenRepository.save(refreshToken.revoke());
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(rt -> {
                    refreshTokenRepository.save(rt.revoke());
                    log.info("=== RefreshToken revoked for {}", rt.userEmail());
                });
    }
}
