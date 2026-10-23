package com.daniellaera.authservice;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3 against the history production has: V1 (with the demo seed) and V2 applied, demo users with refresh
 * tokens, and real accounts that must not be touched. Flyway is configured as in application.yml
 * (classpath:db/migration only), so the dev seed in db/dev is invisible here, as in production.
 */
@Testcontainers
class DemoUsersMigrationITTest {

    private static final List<String> DEMO = List.of("daniel@example.com", "admin@example.com");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway("classpath:db/migration").load().clean();
    }

    @AfterEach
    void cleanUp() {
        flyway("classpath:db/migration").load().clean();
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway(String... locations) {
        return Flyway.configure().dataSource(dataSource).locations(locations).cleanDisabled(false);
    }

    private void prodLikeHistory() {
        flyway("classpath:db/migration").target(MigrationVersion.fromVersion("2")).load().migrate();
        jdbc.update("INSERT INTO users (email, password, role) VALUES ('owner+admin@test.com', 'hash', 'ADMIN')");
        jdbc.update("INSERT INTO users (email, password, role) VALUES ('customer@test.com', 'hash', 'USER')");
        for (String email : List.of("daniel@example.com", "admin@example.com", "admin@example.com",
                "customer@test.com", "owner+admin@test.com")) {
            jdbc.update("INSERT INTO refresh_tokens (token, user_id, expiry_date) "
                    + "SELECT gen_random_uuid()::text, id, now() + interval '1 day' FROM users WHERE email = ?", email);
        }
    }

    private List<Map<String, Object>> otherUsers() {
        return jdbc.queryForList("SELECT id, email, role, password, created_at FROM users "
                + "WHERE email NOT IN ('daniel@example.com', 'admin@example.com') ORDER BY id");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void v3_removesTheDemoUsersAndTheirTokens_andLeavesEveryoneElse() {
        prodLikeHistory();
        assertThat(jdbc.queryForList("SELECT email FROM users WHERE email IN ('daniel@example.com', 'admin@example.com')",
                String.class)).containsExactlyInAnyOrderElementsOf(DEMO);
        List<Map<String, Object>> before = otherUsers();
        int otherTokens = count("SELECT count(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                + "WHERE u.email NOT IN ('daniel@example.com', 'admin@example.com')");

        var result = flyway("classpath:db/migration").load().migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(result.targetSchemaVersion).isEqualTo("3");
        assertThat(count("SELECT count(*) FROM users WHERE email IN ('daniel@example.com', 'admin@example.com')"))
                .isZero();
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id NOT IN (SELECT id FROM users)")).isZero();
        assertThat(count("SELECT count(*) FROM refresh_tokens")).isEqualTo(otherTokens).isEqualTo(2);
        assertThat(otherUsers()).isEqualTo(before);
        // the history validates as it will on the next start in production
        flyway("classpath:db/migration").load().validate();
    }

    @Test
    void v3_isANoOp_whereTheDemoUsersAreAlreadyGone() {
        prodLikeHistory();
        jdbc.update("DELETE FROM users WHERE email IN ('daniel@example.com', 'admin@example.com')");
        List<Map<String, Object>> before = otherUsers();

        var result = flyway("classpath:db/migration").load().migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(otherUsers()).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM refresh_tokens")).isEqualTo(2);
    }

    @Test
    void theDevLocation_seedsTheDemoUsersAgain_afterV3() {
        var result = flyway("classpath:db/migration", "classpath:db/dev").load().migrate();

        assertThat(result.migrations).extracting(m -> m.description)
                .containsExactly("create users table", "add refresh tokens", "remove demo users", "demo users");
        assertThat(jdbc.queryForList("SELECT email || ':' || role FROM users ORDER BY email", String.class))
                .containsExactly("admin@example.com:ADMIN", "daniel@example.com:USER");
        // an existing local database (old V1 seed still there, then V3, then the seed): same result, and a rerun
        // of the repeatable seed keeps the accounts as they are
        flyway("classpath:db/migration", "classpath:db/dev").load().migrate();
        assertThat(count("SELECT count(*) FROM users")).isEqualTo(2);
    }
}
