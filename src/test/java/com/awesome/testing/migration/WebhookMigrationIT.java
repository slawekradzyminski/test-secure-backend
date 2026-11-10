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

/** V14 webhook tables on PostgreSQL: upgrade from V10, constraints and cascades. */
class WebhookMigrationIT {

    private static final String SCHEMA = "webhooks_upgrade";

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
    void v14CreatesTheWebhookTablesWithTheirConstraints() throws Exception {
        flyway(MigrationVersion.fromVersion("10")).migrate();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(tableExists(statement, "webhook_subscriptions")).isFalse();
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO webhook_subscriptions (username, url, events, secret, created_at, updated_at)
                    VALUES ('alice', 'https://example.com/hook', 'order.created', 'encrypted', now(), now())
                    """);
            try (ResultSet result = statement.executeQuery(
                    "SELECT id, active, consecutive_failures FROM webhook_subscriptions WHERE username = 'alice'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(2)).isTrue();
                assertThat(result.getInt(3)).isZero();
            }
            statement.executeUpdate("""
                    INSERT INTO webhook_deliveries (id, subscription_id, event_id, event_type, payload, status, created_at,
                                                    next_attempt_at)
                    SELECT gen_random_uuid(), id, gen_random_uuid(), 'order.created', '{}', 'PENDING', now(), now()
                    FROM webhook_subscriptions WHERE username = 'alice'
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO webhook_deliveries (id, subscription_id, event_id, event_type, payload, status, created_at)
                    SELECT gen_random_uuid(), id, gen_random_uuid(), 'order.created', '{}', 'LOST', now()
                    FROM webhook_subscriptions WHERE username = 'alice'
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO webhook_deliveries (id, subscription_id, event_id, event_type, payload, status, created_at)
                    VALUES (gen_random_uuid(), 999999, gen_random_uuid(), 'order.created', '{}', 'PENDING', now())
                    """)).isInstanceOf(SQLException.class);

            statement.executeUpdate("INSERT INTO webhook_inboxes (token, username, created_at) VALUES ('tok', 'alice', now())");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO webhook_inboxes (token, username, created_at) VALUES ('tok', 'bob', now())"))
                    .isInstanceOf(SQLException.class);
            statement.executeUpdate("""
                    INSERT INTO webhook_inbox_requests (inbox_id, received_at, headers, body)
                    SELECT id, now(), '{}', '{}' FROM webhook_inboxes WHERE token = 'tok'
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO webhook_inbox_requests (inbox_id, received_at, headers, body)
                    SELECT id, now(), '{}', repeat('x', 16385) FROM webhook_inboxes WHERE token = 'tok'
                    """)).isInstanceOf(SQLException.class);

            statement.executeUpdate("DELETE FROM webhook_subscriptions");
            statement.executeUpdate("DELETE FROM webhook_inboxes");
            assertThat(count(statement, "webhook_deliveries")).isZero();
            assertThat(count(statement, "webhook_inbox_requests")).isZero();
        }
    }

    private static boolean tableExists(Statement statement, String table) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT to_regclass('" + table + "') IS NOT NULL")) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private static int count(Statement statement, String table) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
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
