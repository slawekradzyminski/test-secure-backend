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

/** V24: the nullable span id of the writing request on the event feed (docs/TRACING.md). */
class DomainEventSpanIdMigrationIT {

    private static final String SCHEMA = "span_id_upgrade";

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
    void v24AddsANullableSpanIdAndKeepsExistingEvents() throws Exception {
        flyway(MigrationVersion.fromVersion("22")).migrate();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(event("e1", "before-v24", "NULL", "NULL"));
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(single(statement, """
                    SELECT data_type || ':' || character_maximum_length || ':' || is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = '%s' AND table_name = 'domain_events' AND column_name = 'span_id'
                    """.formatted(SCHEMA))).isEqualTo("character varying:16:YES");
            assertThat(single(statement, "SELECT span_id IS NULL FROM domain_events WHERE username = 'before-v24'"))
                    .as("events recorded before V24 have no span id").isEqualTo("t");

            statement.executeUpdate(event("e2", "after-v24", "'4bf92f3577b34da6a3ce929d0e0e4736'", "'00f067aa0ba902b7'"));
            assertThat(single(statement, "SELECT trace_id || '-' || span_id FROM domain_events WHERE username = 'after-v24'"))
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE domain_events SET span_id = repeat('a', 17) WHERE username = 'after-v24'"))
                    .as("span ids are 16 hex characters").isInstanceOf(SQLException.class);
        }

        // Re-running on an already migrated schema is a no-op (IF NOT EXISTS) and Flyway validates.
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }

    private static String event(String suffix, String username, String traceId, String spanId) {
        return """
                INSERT INTO domain_events (event_id, type, order_id, username, occurred_at, recorded_at, payload,
                                           trace_id%s)
                VALUES ('00000000-0000-0000-0000-0000000000%s', 'order.created', 1, '%s', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, '{}', %s%s)
                """.formatted("NULL".equals(spanId) ? "" : ", span_id", suffix, username, traceId,
                "NULL".equals(spanId) ? "" : ", " + spanId);
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
