package com.awesome.testing.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V16: the domain event feed. Undelivered card refunds of the V11 outbox become
 * {@code card-refund.requested} events, counted refunds move to {@code card_refund_requests},
 * and the outbox is dropped.
 */
class DomainEventFeedMigrationIT {

    private static final String SCHEMA = "event_feed_upgrade";
    private static final String PAYMENT = "00000000-0000-0000-0000-000000000002";

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
    void v16MovesTheRefundOutboxIntoTheFeedAndDropsIt() throws Exception {
        flyway(MigrationVersion.fromVersion("15")).migrate();
        long orderId;
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'DELIVERED', 50.00, CURRENT_TIMESTAMP, 'before-v16')
                    """);
            orderId = orderId(statement);
            statement.executeUpdate(outbox(orderId, "00000000-0000-0000-0000-0000000000b1", "12.50", "Return \"7\"",
                    "PENDING", "2026-11-01T10:00:00.123456Z"));
            statement.executeUpdate(outbox(orderId, "00000000-0000-0000-0000-0000000000b2", "5.00", null,
                    "PENDING", "2026-11-01T11:00:00Z"));
            statement.executeUpdate(outbox(orderId, "00000000-0000-0000-0000-0000000000b3", "7.00", "Sent",
                    "SENT", "2026-11-01T09:00:00Z"));
            statement.executeUpdate(outbox(orderId, "00000000-0000-0000-0000-0000000000b4", "3.00", "Skipped",
                    "SKIPPED", "2026-11-01T09:30:00Z"));
            statement.executeUpdate(outbox(orderId, "00000000-0000-0000-0000-0000000000b5", "2.00", "Failed",
                    "FAILED", "2026-11-01T09:45:00Z"));
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT to_regclass('payment_refund_outbox') IS NULL")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).as("outbox dropped").isTrue();
            }

            List<String> events = new ArrayList<>();
            try (ResultSet result = statement.executeQuery("""
                    SELECT event_id, type, order_id, username, occurred_at, recorded_at IS NOT NULL, payload
                    FROM domain_events ORDER BY sequence""")) {
                while (result.next()) {
                    assertThat(result.getString(2)).isEqualTo("card-refund.requested");
                    assertThat(result.getLong(3)).isEqualTo(orderId);
                    assertThat(result.getString(4)).isEqualTo("before-v16");
                    assertThat(result.getBoolean(6)).isTrue();
                    events.add(result.getString(1) + "|" + result.getTimestamp(5).toInstant() + "|" + result.getString(7));
                }
            }
            assertThat(events).containsExactly(
                    eventId("00000000-0000-0000-0000-0000000000b1") + "|2026-11-01T10:00:00.123Z|"
                            + "{\"amount\":12.50,\"orderId\":" + orderId + ",\"reason\":\"Return \\\"7\\\"\","
                            + "\"requestId\":\"00000000-0000-0000-0000-0000000000b1\"}",
                    eventId("00000000-0000-0000-0000-0000000000b2") + "|2026-11-01T11:00:00Z|"
                            + "{\"amount\":5.00,\"orderId\":" + orderId + ",\"reason\":null,"
                            + "\"requestId\":\"00000000-0000-0000-0000-0000000000b2\"}");

            List<String> ledger = new ArrayList<>();
            try (ResultSet result = statement.executeQuery(
                    "SELECT request_id, amount, payment_id FROM card_refund_requests ORDER BY created_at")) {
                while (result.next()) {
                    assertThat(result.getString(3)).isEqualTo(PAYMENT);
                    ledger.add(result.getString(1) + "=" + result.getBigDecimal(2));
                }
            }
            assertThat(ledger).containsExactly(
                    "00000000-0000-0000-0000-0000000000b3=7.00",
                    "00000000-0000-0000-0000-0000000000b1=12.50",
                    "00000000-0000-0000-0000-0000000000b2=5.00");

            // New feed rows get increasing sequences; event ids are unique.
            statement.executeUpdate(event("00000000-0000-0000-0000-0000000000c1", orderId));
            assertThatThrownBy(() -> statement.executeUpdate(event("00000000-0000-0000-0000-0000000000c1", orderId)))
                    .isInstanceOf(SQLException.class);
            try (ResultSet result = statement.executeQuery(
                    "SELECT max(sequence) = (SELECT sequence FROM domain_events WHERE event_id = "
                            + "'00000000-0000-0000-0000-0000000000c1') FROM domain_events")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isTrue();
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = '" + SCHEMA
                            + "' AND indexname = 'idx_domain_events_recorded_at'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isOne();
            }

            assertThatThrownBy(() -> statement.executeUpdate(ledgerRow(orderId, "00000000-0000-0000-0000-0000000000d1", "0.00")))
                    .as("positive amounts only").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(ledgerRow(orderId, "00000000-0000-0000-0000-0000000000b3", "1.00")))
                    .as("one row per requestId").isInstanceOf(SQLException.class);

            statement.executeUpdate("DELETE FROM orders WHERE id = " + orderId);
            try (ResultSet result = statement.executeQuery("SELECT count(*) FROM card_refund_requests")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).as("ledger follows its order").isZero();
            }
            try (ResultSet result = statement.executeQuery("SELECT count(*) FROM domain_events")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).as("feed rows are kept until retention").isEqualTo(3);
            }
        }
    }

    /** {@code md5('card-refund.requested:' || requestId)::uuid}, as V16 derives it. */
    private static String eventId(String requestId) throws Exception {
        byte[] md5 = MessageDigest.getInstance("MD5")
                .digest(("card-refund.requested:" + requestId).getBytes(StandardCharsets.UTF_8));
        String hex = HexFormat.of().formatHex(md5);
        return UUID.fromString(hex.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5")).toString();
    }

    private static String outbox(long orderId, String requestId, String amount, String reason, String status,
                                 String createdAt) {
        return """
                INSERT INTO payment_refund_outbox (request_id, order_id, payment_id, amount, reason, status,
                                                   created_at, updated_at)
                VALUES ('%s', %d, '%s', %s, %s, '%s', '%s', '%s')
                """.formatted(requestId, orderId, PAYMENT, amount,
                reason == null ? "NULL" : "'" + reason.replace("'", "''") + "'", status, createdAt, createdAt);
    }

    private static String event(String eventId, long orderId) {
        return """
                INSERT INTO domain_events (event_id, type, order_id, username, occurred_at, recorded_at, payload)
                VALUES ('%s', 'order.created', %d, 'before-v16', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}')
                """.formatted(eventId, orderId);
    }

    private static String ledgerRow(long orderId, String requestId, String amount) {
        return """
                INSERT INTO card_refund_requests (request_id, order_id, payment_id, amount, reason, created_at)
                VALUES ('%s', %d, '%s', %s, 'x', CURRENT_TIMESTAMP)
                """.formatted(requestId, orderId, PAYMENT, amount);
    }

    private static long orderId(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT id FROM orders WHERE username = 'before-v16'")) {
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
