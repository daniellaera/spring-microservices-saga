package com.daniellaera.authservice.adapter.out.persistence;

import com.daniellaera.authservice.domain.model.RefreshToken;
import com.daniellaera.authservice.domain.model.UserNotFoundException;
import com.daniellaera.authservice.domain.port.out.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Called inside the use cases' transactions: the token's user is a lazy association, and the bulk update needs one. */
@Component
@RequiredArgsConstructor
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenJpaRepository.findByToken(token).map(AuthJpaMapper::toDomain);
    }

    // a known token is updated (only its revocation changes), a new one stored for its user
    @Override
    public RefreshToken save(RefreshToken token) {
        Optional<RefreshTokenJpaEntity> existing = refreshTokenJpaRepository.findByToken(token.token());
        if (existing.isPresent()) {
            RefreshTokenJpaEntity entity = existing.get();
            entity.setRevoked(token.revoked());
            return AuthJpaMapper.toDomain(refreshTokenJpaRepository.save(entity));
        }
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setToken(token.token());
        entity.setUser(userOf(token.userEmail()));
        entity.setExpiryDate(token.expiryDate());
        entity.setRevoked(token.revoked());
        return AuthJpaMapper.toDomain(refreshTokenJpaRepository.save(entity));
    }

    @Override
    public void revokeAllOf(String userEmail) {
        refreshTokenJpaRepository.revokeAllUserTokens(userOf(userEmail));
    }

    private UserJpaEntity userOf(String email) {
        return userJpaRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }
}
