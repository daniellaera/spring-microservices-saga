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

/** Runs the real migrations against data written by the previous schema version, one fresh database per test. */
class TransactionMigrationITTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private DataSource dataSource;

    @BeforeAll
    static void start() {
        postgres.start();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    @Test
    void v3_keepsTheEarliestTransactionPerOrderThenMakesOrderIdUnique() {
        freshDatabase("v3");
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

    @Test
    void v4_existingRowsHaveNoIntent_andAPayingIntentIsUniqueWhenSet() {
        freshDatabase("v4");
        flyway("3").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO transaction (order_id, status, total_amount) VALUES (100, 'SUCCESS', 10.00)");

        flyway("4").migrate();

        assertThat(jdbc.queryForObject("SELECT payment_intent_id FROM transaction WHERE order_id = 100", String.class))
                .isNull();
        jdbc.update("INSERT INTO transaction (order_id, status, payment_intent_id) VALUES (101, 'FAILED', NULL)");
        jdbc.update("INSERT INTO transaction (order_id, status, payment_intent_id) VALUES (102, 'SUCCESS', 'pi_m')");
        assertThatThrownBy(() ->
                jdbc.update("INSERT INTO transaction (order_id, status, payment_intent_id) VALUES (103, 'SUCCESS', 'pi_m')"))
                .hasMessageContaining("uk_transaction_payment_intent_id");
    }

    @Test
    void v5_existingRowsHaveNoRefund() {
        freshDatabase("v5");
        flyway("4").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO transaction (order_id, status, total_amount) VALUES (100, 'FAILED', 10.00)");

        flyway("5").migrate();

        assertThat(jdbc.queryForObject("SELECT refund_id FROM transaction WHERE order_id = 100", String.class))
                .isNull();
        jdbc.update("INSERT INTO transaction (order_id, status, payment_intent_id, refund_id) "
                + "VALUES (101, 'FAILED', 'pi_r', 're_r')");
        assertThat(jdbc.queryForObject("SELECT refund_id FROM transaction WHERE order_id = 101", String.class))
                .isEqualTo("re_r");
    }

    private void freshDatabase(String name) {
        DataSource admin = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        new JdbcTemplate(admin).execute("CREATE DATABASE " + name);
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name),
                postgres.getUsername(), postgres.getPassword());
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }
}
