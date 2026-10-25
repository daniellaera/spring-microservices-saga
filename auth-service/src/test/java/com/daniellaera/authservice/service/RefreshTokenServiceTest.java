package com.daniellaera.authservice.service;

import com.daniellaera.authservice.audit.AuditPublisher;
import com.daniellaera.authservice.domain.model.Role;
import com.daniellaera.authservice.adapter.out.persistence.RefreshTokenJpaEntity;
import com.daniellaera.authservice.adapter.out.persistence.UserJpaEntity;
import com.daniellaera.authservice.adapter.out.persistence.RefreshTokenJpaRepository;
import com.daniellaera.authservice.adapter.out.persistence.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenJpaRepository refreshTokenRepository;
    @Mock private UserJpaRepository userRepository;
    @Mock private AuditPublisher auditPublisher;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void createRefreshToken_shouldCreateTokenForValidUser() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", 604800000L);
        UserJpaEntity user = UserJpaEntity.builder().email("john@test.com").role(Role.USER).build();
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshTokenJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenJpaEntity refreshToken = refreshTokenService.createRefreshToken("john@test.com");

        assertThat(refreshToken.getToken()).isNotBlank();
        assertThat(refreshToken.getUser()).isEqualTo(user);
        assertThat(refreshToken.isRevoked()).isFalse();
        assertThat(refreshToken.getExpiryDate()).isAfter(Instant.now());
        verify(auditPublisher).publish(eq("TOKEN_REFRESHED"), any(), any(), any(), any());
    }

    @Test
    void createRefreshToken_shouldRevokeExistingTokensFirst() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", 604800000L);
        UserJpaEntity user = UserJpaEntity.builder().email("john@test.com").role(Role.USER).build();
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshTokenJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        refreshTokenService.createRefreshToken("john@test.com");

        ArgumentCaptor<UserJpaEntity> userCaptor = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(refreshTokenRepository, times(1)).revokeAllUserTokens(userCaptor.capture());
        assertThat(userCaptor.getValue()).isEqualTo(user);
    }

    @Test
    void createRefreshToken_shouldThrow_whenUserNotFound() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.createRefreshToken("missing@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing@test.com");
    }

    @Test
    void validateRefreshToken_shouldReturnToken_whenValid() {
        RefreshTokenJpaEntity token = new RefreshTokenJpaEntity();
        token.setToken("valid-token");
        token.setRevoked(false);
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        RefreshTokenJpaEntity result = refreshTokenService.validateRefreshToken("valid-token");

        assertThat(result).isEqualTo(token);
    }

    @Test
    void validateRefreshToken_shouldThrow_whenNotFound() {
        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("missing-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void validateRefreshToken_shouldThrow_whenRevoked() {
        RefreshTokenJpaEntity token = new RefreshTokenJpaEntity();
        token.setToken("revoked-token");
        token.setRevoked(true);
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("revoked-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void validateRefreshToken_shouldThrowAndRevoke_whenExpired() {
        RefreshTokenJpaEntity token = new RefreshTokenJpaEntity();
        token.setToken("expired-token");
        token.setRevoked(false);
        token.setExpiryDate(Instant.now().minusSeconds(3600));
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("expired-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(1)).save(token);
    }

    @Test
    void revokeRefreshToken_shouldSetRevokedTrue() {
        UserJpaEntity user = UserJpaEntity.builder().email("john@test.com").role(Role.USER).build();
        RefreshTokenJpaEntity token = new RefreshTokenJpaEntity();
        token.setToken("some-token");
        token.setUser(user);
        token.setRevoked(false);
        when(refreshTokenRepository.findByToken("some-token")).thenReturn(Optional.of(token));

        refreshTokenService.revokeRefreshToken("some-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(1)).save(token);
    }

    @Test
    void revokeRefreshToken_shouldDoNothing_whenTokenNotFound() {
        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        refreshTokenService.revokeRefreshToken("missing-token");

        verify(refreshTokenRepository, never()).save(any());
    }
}
