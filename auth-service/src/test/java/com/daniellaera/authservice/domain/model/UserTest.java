package com.daniellaera.authservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 8, 0);

    @Test
    void aRegisteredUser_isAUser_withoutNames() {
        User user = User.register("ada@example.com", "hash", NOW);

        assertThat(user).isEqualTo(new User(null, "ada@example.com", "hash", null, null, Role.USER, NOW));
    }

    @Test
    void theDisplayName_isTheNames_orTheFirstName_orTheEmailsLocalPart() {
        User user = User.register("ada@example.com", "hash", NOW);

        assertThat(user.displayName()).isEqualTo("ada");
        assertThat(user.withNames("Ada", null).displayName()).isEqualTo("Ada");
        assertThat(user.withNames("Ada", "Lovelace").displayName()).isEqualTo("Ada Lovelace");
        assertThat(user.withNames(null, "Lovelace").displayName()).isEqualTo("ada");
    }

    @Test
    void aNameNotGiven_isKept() {
        User named = User.register("ada@example.com", "hash", NOW).withNames("Ada", "Lovelace");

        assertThat(named.withNames(null, "Byron")).extracting(User::firstName, User::lastName)
                .containsExactly("Ada", "Byron");
        assertThat(named.withNames(null, null)).isEqualTo(named);
    }

    @Test
    void aRefreshToken_expiresAfterItsDate_andIsRevokedOnce() {
        Instant expiry = Instant.parse("2026-11-01T08:00:00Z");
        RefreshToken token = RefreshToken.issue("t", "ada@example.com", Role.USER, expiry);

        assertThat(token.revoked()).isFalse();
        assertThat(token.isExpired(expiry.minusSeconds(1))).isFalse();
        assertThat(token.isExpired(expiry.plusSeconds(1))).isTrue();
        assertThat(token.revoke().revoked()).isTrue();
        assertThat(token.revoke()).extracting(RefreshToken::token, RefreshToken::expiryDate).containsExactly("t", expiry);
    }
}
