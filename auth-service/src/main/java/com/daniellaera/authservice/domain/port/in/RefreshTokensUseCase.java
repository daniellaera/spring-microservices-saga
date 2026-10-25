package com.daniellaera.authservice.domain.port.in;

import com.daniellaera.authservice.domain.model.AuthTokens;

public interface RefreshTokensUseCase {

    // the refresh token is revoked and replaced
    AuthTokens refresh(String refreshToken);

    // revokes it; an unknown token is ignored
    void logout(String refreshToken);
}
