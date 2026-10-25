package com.daniellaera.authservice.domain.port.out;

import com.daniellaera.authservice.domain.model.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByToken(String token);

    // a new token is stored for its user, a known one updated (its revocation)
    RefreshToken save(RefreshToken token);

    void revokeAllOf(String userEmail);
}
