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

/**
 * V18: case-insensitive unique indexes on usernames and emails. On a clean table they are created; when
 * accounts already collide the migration skips the affected index instead of failing the deployment.
 */
class CaseInsensitiveIdentityMigrationIT {

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
    void withoutCollisionsBothIndexesAreCreatedAndRejectCaseVariants() throws Exception {
        String schema = "identity_clean";
        flyway(schema, MigrationVersion.fromVersion("17")).migrate();
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO app_user (email, username) VALUES ('a@example.com', 'alice')");
        }

        flyway(schema, null).migrate();

        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            assertThat(indexes(statement)).isEqualTo("ux_app_user_email_lower,ux_app_user_username_lower");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO app_user (email, username) VALUES ('other@example.com', 'ALICE')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO app_user (email, username) VALUES ('A@Example.com', 'bob1')"))
                    .isInstanceOf(SQLException.class);
            statement.executeUpdate("INSERT INTO app_user (email, username) VALUES ('b@example.com', 'bob1')");
        }
        assertThat(flyway(schema, null).migrate().migrationsExecuted).isZero();
    }

    @Test
    void existingCollisionsSkipOnlyTheAffectedIndexAndKeepTheAccounts() throws Exception {
        String schema = "identity_legacy";
        flyway(schema, MigrationVersion.fromVersion("17")).migrate();
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO app_user (email, username) VALUES ('x@example.com', 'Abc1')");
            statement.executeUpdate("INSERT INTO app_user (email, username) VALUES ('y@example.com', 'abc1')");
        }

        flyway(schema, null).migrate();

        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            assertThat(indexes(statement)).isEqualTo("ux_app_user_email_lower");
            try (ResultSet result = statement.executeQuery("SELECT count(*) FROM app_user")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
        }
    }

    private static String indexes(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("""
                SELECT string_agg(indexname, ',' ORDER BY indexname) FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename = 'app_user' AND indexname LIKE 'ux_app_user_%_lower'
                """)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static Connection connect(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
        }
        return connection;
    }

    private static Flyway flyway(String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .createSchemas(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
