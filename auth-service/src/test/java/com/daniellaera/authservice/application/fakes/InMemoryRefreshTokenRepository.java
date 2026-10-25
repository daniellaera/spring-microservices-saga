package com.daniellaera.authservice.application.fakes;

import com.daniellaera.authservice.domain.model.RefreshToken;
import com.daniellaera.authservice.domain.port.out.RefreshTokenRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<String, RefreshToken> tokens = new LinkedHashMap<>();

    public void given(RefreshToken token) {
        tokens.put(token.token(), token);
    }

    public List<RefreshToken> all() {
        return List.copyOf(tokens.values());
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return Optional.ofNullable(tokens.get(token));
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        tokens.put(token.token(), token);
        return token;
    }

    @Override
    public void revokeAllOf(String userEmail) {
        tokens.replaceAll((key, token) -> token.userEmail().equals(userEmail) ? token.revoke() : token);
    }
}
