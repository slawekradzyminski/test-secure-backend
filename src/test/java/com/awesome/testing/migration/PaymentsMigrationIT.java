package com.awesome.testing.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V11: payment results, per-order payments and the card refund outbox (replaced by V16). */
class PaymentsMigrationIT {

    private static final String SCHEMA = "payments_upgrade";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine@sha256:"
                    + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @BeforeAll
    static void startPostgres() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void v11AddsPaymentTablesThatFollowTheirOrder() throws Exception {
        flyway(MigrationVersion.fromVersion("10")).migrate();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'PENDING', 20.00, CURRENT_TIMESTAMP, 'before-v11')
                    """);
        }

        flyway(MigrationVersion.fromVersion("15")).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            long orderId = orderId(statement);
            statement.executeUpdate(payment(orderId, "00000000-0000-0000-0000-000000000001", "FAILED"));
            statement.executeUpdate(payment(orderId, "00000000-0000-0000-0000-000000000002", "CAPTURED"));
            assertThatThrownBy(() -> statement.executeUpdate(
                    payment(orderId, "00000000-0000-0000-0000-000000000003", "CAPTURED")))
                    .as("one captured payment per order").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    payment(orderId, "00000000-0000-0000-0000-000000000002", "FAILED")))
                    .as("payment ids are unique").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    payment(orderId, "00000000-0000-0000-0000-000000000004", "VOIDED")))
                    .isInstanceOf(SQLException.class);

            statement.executeUpdate("""
                    INSERT INTO payment_notifications (request_id, order_id, payment_id, status, order_status, created_at)
                    VALUES ('00000000-0000-0000-0000-0000000000a1', %d, '00000000-0000-0000-0000-000000000002',
                            'CAPTURED', 'PAID', CURRENT_TIMESTAMP)
                    """.formatted(orderId));
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO payment_notifications (request_id, order_id, payment_id, status, order_status, created_at)
                    VALUES ('00000000-0000-0000-0000-0000000000a1', %d, '00000000-0000-0000-0000-000000000002',
                            'CAPTURED', 'PAID', CURRENT_TIMESTAMP)
                    """.formatted(orderId))).isInstanceOf(SQLException.class);

            statement.executeUpdate(outbox(orderId, "00000000-0000-0000-0000-0000000000b1", "5.00", "PENDING"));
            assertThatThrownBy(() -> statement.executeUpdate(
                    outbox(orderId, "00000000-0000-0000-0000-0000000000b2", "0.00", "PENDING")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    outbox(orderId, "00000000-0000-0000-0000-0000000000b3", "1.00", "LOST")))
                    .isInstanceOf(SQLException.class);
            try (ResultSet result = statement.executeQuery(
                    "SELECT attempts, next_attempt_at IS NULL FROM payment_refund_outbox")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }

            statement.executeUpdate("DELETE FROM orders WHERE id = " + orderId);
            for (String table : new String[] {"order_payments", "payment_notifications", "payment_refund_outbox"}) {
                try (ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).as(table).isZero();
                }
            }
        }
    }

    private static String payment(long orderId, String paymentId, String status) {
        return """
                INSERT INTO order_payments (order_id, payment_id, status, amount, currency, created_at)
                VALUES (%d, '%s', '%s', 20.00, 'USD', CURRENT_TIMESTAMP)
                """.formatted(orderId, paymentId, status);
    }

    private static String outbox(long orderId, String requestId, String amount, String status) {
        return """
                INSERT INTO payment_refund_outbox (request_id, order_id, payment_id, amount, reason, status,
                                                   created_at, updated_at)
                VALUES ('%s', %d, '00000000-0000-0000-0000-000000000002', %s, 'Return', '%s',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(requestId, orderId, amount, status);
    }

    private static long orderId(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT id FROM orders WHERE username = 'before-v11'")) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + SCHEMA);
        }
        return connection;
    }

    private static Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema(SCHEMA)
                .schemas(SCHEMA)
                .createSchemas(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
