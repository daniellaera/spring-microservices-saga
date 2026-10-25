package com.daniellaera.authservice.adapter.out.persistence;

import com.daniellaera.authservice.TestcontainersConfiguration;
import com.daniellaera.authservice.domain.model.EmailAlreadyRegisteredException;
import com.daniellaera.authservice.domain.model.RefreshToken;
import com.daniellaera.authservice.domain.model.Role;
import com.daniellaera.authservice.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Both adapters on PostgreSQL with the Flyway schema. @DataJpaTest: each test runs in a transaction, rolled back. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UserPersistenceAdapter.class, RefreshTokenPersistenceAdapter.class})
class AuthPersistenceAdaptersITTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 25, 8, 0);

    @Autowired
    private UserPersistenceAdapter users;

    @Autowired
    private RefreshTokenPersistenceAdapter tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void empty() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void aNewUser_isStored_andFoundByEmail() {
        User saved = users.save(User.register("ada@example.com", "$2a$10$hash", CREATED));

        assertThat(saved.id()).isNotNull();
        assertThat(users.findByEmail("ada@example.com")).contains(saved);
        assertThat(users.existsByEmail("ada@example.com")).isTrue();
        assertThat(users.existsByEmail("nobody@example.com")).isFalse();
        assertThat(users.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void aSecondUserWithTheSameEmail_isRefused() {
        users.save(User.register("ada@example.com", "hash", CREATED));

        assertThatThrownBy(() -> users.save(User.register("ada@example.com", "other", CREATED)))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("An account with this email already exists");
    }

    @Test
    void anExistingUser_isUpdated_keepingItsPasswordAndRole() {
        User saved = users.save(User.register("ada@example.com", "hash", CREATED));

        users.save(saved.withNames("Ada", "Lovelace"));

        assertThat(users.findByEmail("ada@example.com")).hasValueSatisfying(user -> {
            assertThat(user.firstName()).isEqualTo("Ada");
            assertThat(user.lastName()).isEqualTo("Lovelace");
            assertThat(user.passwordHash()).isEqualTo("hash");
            assertThat(user.role()).isEqualTo(Role.USER);
            assertThat(user.createdAt()).isEqualTo(CREATED);
        });
    }

    @Test
    void aRefreshToken_isStoredForItsUser_revoked_andAllOfAUsersRevokedAtOnce() {
        users.save(User.register("ada@example.com", "hash", CREATED));
        Instant expiry = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

        tokens.save(RefreshToken.issue("t1", "ada@example.com", Role.USER, expiry));
        tokens.save(RefreshToken.issue("t2", "ada@example.com", Role.USER, expiry));

        assertThat(tokens.findByToken("t1")).contains(new RefreshToken("t1", "ada@example.com", Role.USER, expiry, false));

        tokens.save(tokens.findByToken("t1").orElseThrow().revoke());
        assertThat(tokens.findByToken("t1")).hasValueSatisfying(token -> assertThat(token.revoked()).isTrue());
        assertThat(tokens.findByToken("t2")).hasValueSatisfying(token -> assertThat(token.revoked()).isFalse());

        tokens.revokeAllOf("ada@example.com");
        jdbc.queryForList("SELECT revoked FROM refresh_tokens")
                .forEach(row -> assertThat(row.get("revoked")).isEqualTo(true));
        assertThat(tokens.findByToken("unknown")).isEmpty();
    }
}
