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

/** V20 (catalog X-03): traffic owners, session bindings and the masking state for the history migration. */
class TrafficOwnerMigrationIT {

    private static final String SCHEMA = "traffic_owner_upgrade";

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
    void v20KeepsStoredTrafficWithoutOwnerAndStartsTheHistoryMaskingAtVersionZero() throws Exception {
        flyway(MigrationVersion.fromVersion("19")).migrate();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO traffic_log (correlation_id, client_session_id, duration_ms, method, path, status,
                                             "timestamp", request_body)
                    VALUES ('before-v20', 'session-before-v20', 1, 'POST', '/api/v1/users/signin', 200, now(),
                            '{"password":"stored-in-clear"}')""");
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(single(statement, "SELECT owner_username IS NULL FROM traffic_log WHERE correlation_id = 'before-v20'"))
                    .as("rows stored before V20 have no owner").isEqualTo("t");
            assertThat(single(statement, "SELECT request_body FROM traffic_log"))
                    .as("the history is masked by the application on start, not by SQL")
                    .isEqualTo("{\"password\":\"stored-in-clear\"}");
            assertThat(single(statement, "SELECT id || ':' || rules_version FROM traffic_masking_state"))
                    .isEqualTo("1:0");
            statement.executeUpdate(
                    "INSERT INTO traffic_session_owner (client_session_id, username, bound_at) VALUES ('s1', 'alice', now())");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO traffic_session_owner (client_session_id, username, bound_at) VALUES ('s1', 'bob', now())"))
                    .as("a session has one owner").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO traffic_masking_state VALUES (2, 0)"))
                    .as("a single state row").isInstanceOf(SQLException.class);
        }
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
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
