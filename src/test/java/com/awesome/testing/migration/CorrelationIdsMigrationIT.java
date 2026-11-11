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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V17: nullable correlation columns on the event feed, the email outbox and webhook deliveries.
 * Rows written before the migration keep null ids; new rows store them, bounded in length.
 */
class CorrelationIdsMigrationIT {

    private static final String SCHEMA = "correlation_upgrade";
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

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
    void v17AddsNullableCorrelationColumnsAndKeepsExistingRows() throws Exception {
        flyway(MigrationVersion.fromVersion("16")).migrate();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO domain_events (event_id, type, order_id, username, occurred_at, recorded_at, payload)
                    VALUES ('00000000-0000-0000-0000-0000000000e1', 'order.created', 1, 'before-v17',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}')
                    """);
            statement.executeUpdate("INSERT INTO app_user (email, username) VALUES ('corr@example.test', 'corr-user')");
            statement.executeUpdate("""
                    INSERT INTO email_event (created_at, recipient_email, status, type, updated_at, user_id, request_id)
                    SELECT CURRENT_TIMESTAMP, email, 'QUEUED', 'GENERIC', CURRENT_TIMESTAMP, id,
                           '00000000-0000-0000-0000-0000000000f1'
                    FROM app_user WHERE username = 'corr-user'
                    """);
            statement.executeUpdate("""
                    INSERT INTO webhook_subscriptions (username, url, events, secret, created_at, updated_at)
                    VALUES ('corr-user', 'https://example.com/hook', 'order.created', 'encrypted', now(), now())
                    """);
            statement.executeUpdate(delivery("'00000000-0000-0000-0000-0000000000a1'", null, null));
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(columns("domain_events")).contains("request_id:character varying:128:YES",
                    "trace_id:character varying:32:YES");
            assertThat(columns("email_event")).contains("correlation_request_id:character varying:128:YES",
                    "traceparent:character varying:55:YES", "request_id:uuid:null:YES");
            assertThat(columns("webhook_deliveries")).contains("request_id:character varying:128:YES",
                    "traceparent:character varying:55:YES");

            assertThat(single(statement, "SELECT request_id IS NULL AND trace_id IS NULL FROM domain_events"))
                    .as("events recorded before V17 have no ids").isEqualTo("t");
            assertThat(single(statement, """
                    SELECT request_id::text || '|' || (correlation_request_id IS NULL) || '|' || (traceparent IS NULL)
                    FROM email_event"""))
                    .as("the notification idempotency key is untouched").isEqualTo("00000000-0000-0000-0000-0000000000f1|true|true");
            assertThat(single(statement, "SELECT request_id IS NULL AND traceparent IS NULL FROM webhook_deliveries"))
                    .isEqualTo("t");

            statement.executeUpdate("""
                    INSERT INTO domain_events (event_id, type, order_id, username, occurred_at, recorded_at, payload,
                                               request_id, trace_id)
                    VALUES ('00000000-0000-0000-0000-0000000000e2', 'order.paid', 1, 'after-v17', CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP, '{}', '%s', '4bf92f3577b34da6a3ce929d0e0e4736')
                    """.formatted("r".repeat(128)));
            statement.executeUpdate(delivery("gen_random_uuid()", "'smoke-123'", "'" + TRACEPARENT + "'"));
            assertThat(single(statement, "SELECT count(*) FROM webhook_deliveries WHERE request_id = 'smoke-123'"))
                    .isEqualTo("1");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE domain_events SET request_id = '"
                    + "r".repeat(129) + "' WHERE username = 'after-v17'"))
                    .as("request ids are at most 128 characters").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE domain_events SET trace_id = repeat('a', 33) WHERE username = 'after-v17'"))
                    .isInstanceOf(SQLException.class);
        }

        // Re-running on an already migrated schema is a no-op (IF NOT EXISTS) and Flyway validates.
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }

    private static String delivery(String id, String requestId, String traceparent) {
        return """
                INSERT INTO webhook_deliveries (id, subscription_id, event_id, event_type, payload, status, created_at%s)
                SELECT %s, id, gen_random_uuid(), 'order.created', '{}', 'PENDING', now()%s
                FROM webhook_subscriptions WHERE username = 'corr-user'
                """.formatted(requestId == null ? "" : ", request_id, traceparent", id,
                requestId == null ? "" : ", " + requestId + ", " + traceparent);
    }

    private static List<String> columns(String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT column_name, data_type, character_maximum_length, is_nullable
                     FROM information_schema.columns WHERE table_schema = '%s' AND table_name = '%s'
                     """.formatted(SCHEMA, table))) {
            while (result.next()) {
                columns.add(result.getString(1) + ":" + result.getString(2) + ":" + result.getString(3) + ":"
                        + result.getString(4));
            }
        }
        return columns;
    }

    private static String single(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            String value = result.getString(1);
            assertThat(result.next()).isFalse();
            return value;
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
