package com.daniellaera.authservice.application;

import com.daniellaera.authservice.application.fakes.InMemoryRefreshTokenRepository;
import com.daniellaera.authservice.application.fakes.InMemoryUserRepository;
import com.daniellaera.authservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.authservice.domain.model.InvalidRefreshTokenException;
import com.daniellaera.authservice.domain.model.RefreshToken;
import com.daniellaera.authservice.domain.model.Role;
import com.daniellaera.authservice.domain.model.User;
import com.daniellaera.authservice.domain.model.UserNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RefreshTokenServiceTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryRefreshTokenRepository tokens = new InMemoryRefreshTokenRepository();
    private final RecordingAuditTrail audit = new RecordingAuditTrail();
    private final RefreshTokenService service = new RefreshTokenService(tokens, users, audit, Duration.ofDays(7));

    {
        users.given(User.register("john@test.com", "hash", LocalDateTime.now()));
    }

    @Test
    void create_issuesASevenDayTokenForTheUser_andAuditsIt() {
        RefreshToken token = service.createRefreshToken("john@test.com");

        assertThat(token.token()).matches("[0-9a-f-]{36}");
        assertThat(token.userEmail()).isEqualTo("john@test.com");
        assertThat(token.userRole()).isEqualTo(Role.USER);
        assertThat(token.revoked()).isFalse();
        assertThat(token.expiryDate()).isCloseTo(Instant.now().plus(7, ChronoUnit.DAYS), within(5, ChronoUnit.SECONDS));
        assertThat(audit.events()).containsExactly("TOKEN_REFRESHED john@test.com");
    }

    @Test
    void create_revokesTheUsersOtherTokensFirst() {
        RefreshToken first = service.createRefreshToken("john@test.com");

        RefreshToken second = service.createRefreshToken("john@test.com");

        assertThat(tokens.findByToken(first.token())).hasValueSatisfying(t -> assertThat(t.revoked()).isTrue());
        assertThat(tokens.findByToken(second.token())).hasValueSatisfying(t -> assertThat(t.revoked()).isFalse());
    }

    @Test
    void create_forAnUnknownUser_fails() {
        assertThatThrownBy(() -> service.createRefreshToken("nobody@test.com"))
                .isInstanceOf(UserNotFoundException.class).hasMessage("User not found: nobody@test.com");
        assertThat(tokens.all()).isEmpty();
    }

    @Test
    void validate_answersAValidToken() {
        RefreshToken token = service.createRefreshToken("john@test.com");

        assertThat(service.validateRefreshToken(token.token())).isEqualTo(token);
    }

    @Test
    void validate_refusesAnUnknownOrRevokedToken() {
        tokens.given(new RefreshToken("revoked", "john@test.com", Role.USER, Instant.now().plusSeconds(60), true));

        assertThatThrownBy(() -> service.validateRefreshToken("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class).hasMessage("Refresh token not found");
        assertThatThrownBy(() -> service.validateRefreshToken("revoked"))
                .isInstanceOf(InvalidRefreshTokenException.class).hasMessage("Refresh token revoked");
    }

    // here the revocation sticks: the rollback that undoes it comes from the bean's transaction (AuthHttpContractITTest)
    @Test
    void validate_refusesAnExpiredToken_andRevokesIt() {
        tokens.given(RefreshToken.issue("expired", "john@test.com", Role.USER, Instant.now().minusSeconds(60)));

        assertThatThrownBy(() -> service.validateRefreshToken("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class).hasMessage("Refresh token expired");
        assertThat(tokens.findByToken("expired")).hasValueSatisfying(t -> assertThat(t.revoked()).isTrue());
    }

    @Test
    void revoke_revokesAKnownToken_andIgnoresAnUnknownOne() {
        RefreshToken token = service.createRefreshToken("john@test.com");

        service.revokeRefreshToken(token.token());
        service.revokeRefreshToken("unknown");

        assertThat(tokens.findByToken(token.token())).hasValueSatisfying(t -> assertThat(t.revoked()).isTrue());
        assertThat(tokens.all()).hasSize(1);
    }
}
