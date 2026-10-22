package com.daniellaera.paymentservice.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real migrations against data written by the previous schema version. */
class TransactionMigrationITTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    private static DataSource dataSource;

    @BeforeAll
    static void start() {
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    @Test
    void v3_keepsTheEarliestTransactionPerOrderThenMakesOrderIdUnique() {
        flyway("2").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO transaction (order_id, status, total_amount) VALUES (1, 'SUCCESS', 10.00)");
        jdbc.update("INSERT INTO transaction (order_id, status, total_amount) VALUES (1, 'SUCCESS', 10.00)");
        jdbc.update("INSERT INTO transaction (order_id, status, total_amount) VALUES (2, 'FAILED', 20.00)");
        jdbc.update("INSERT INTO transaction (order_id, status, total_amount) VALUES (1, 'FAILED', 10.00)");
        Long earliestForOrder1 = jdbc.queryForObject("SELECT min(id) FROM transaction WHERE order_id = 1", Long.class);

        flyway("3").migrate();

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, order_id, status FROM transaction ORDER BY id");
        assertThat(rows).extracting(row -> row.get("order_id")).containsExactly(1L, 2L);
        assertThat(rows.get(0).get("id")).isEqualTo(earliestForOrder1);
        assertThat(rows.get(0).get("status")).isEqualTo("SUCCESS");
        assertThatThrownBy(() ->
                jdbc.update("INSERT INTO transaction (order_id, status, total_amount) VALUES (2, 'SUCCESS', 20.00)"))
                .hasMessageContaining("uk_transaction_order_id");
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }
}
