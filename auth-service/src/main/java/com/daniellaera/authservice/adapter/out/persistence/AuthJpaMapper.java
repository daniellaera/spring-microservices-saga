package com.daniellaera.authservice.adapter.out.persistence;

import com.daniellaera.authservice.domain.model.RefreshToken;
import com.daniellaera.authservice.domain.model.User;

final class AuthJpaMapper {

    private AuthJpaMapper() {
    }

    static User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getEmail(), entity.getPassword(), entity.getFirstName(),
                entity.getLastName(), entity.getRole(), entity.getCreatedAt());
    }

    static UserJpaEntity toEntity(User user) {
        return UserJpaEntity.builder()
                .id(user.id())
                .email(user.email())
                .password(user.passwordHash())
                .firstName(user.firstName())
                .lastName(user.lastName())
                .role(user.role())
                .createdAt(user.createdAt())
                .build();
    }

    // reads the user: called inside a transaction (the association is lazy)
    static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return new RefreshToken(entity.getToken(), entity.getUser().getEmail(), entity.getUser().getRole(),
                entity.getExpiryDate(), entity.isRevoked());
    }
}
