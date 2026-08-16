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
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMigrationIT {

    private static final String FRESH_SCHEMA = "inventory_fresh";
    private static final String UPGRADE_SCHEMA = "inventory_upgrade";

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
    void v4CreatesTheInventorySchemaOnAPostgresFreshDatabase() throws Exception {
        flyway(FRESH_SCHEMA, null).migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(columnExists(statement, FRESH_SCHEMA, "products", "version")).isTrue();
            assertThat(columnExists(statement, FRESH_SCHEMA, "orders", "inventory_state")).isTrue();
            assertThat(columnExists(statement, FRESH_SCHEMA, "inventory_movements", "request_id")).isTrue();
            assertThat(foreignKeyRule(statement, FRESH_SCHEMA, "inventory_movements", "product_id"))
                    .isEqualTo("CASCADE");
            assertThat(foreignKeyRule(statement, FRESH_SCHEMA, "inventory_movements", "order_id"))
                    .isEqualTo("SET NULL");
        }
    }

    @Test
    void v4MarksExistingOrdersAsLegacyUntrackedDuringUpgrade() throws Exception {
        flyway(UPGRADE_SCHEMA, MigrationVersion.fromVersion("3")).migrate();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + UPGRADE_SCHEMA);
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'PENDING', 10.00, CURRENT_TIMESTAMP, 'legacy-user')
                    """);
        }

        flyway(UPGRADE_SCHEMA, null).migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + UPGRADE_SCHEMA);
            try (ResultSet result = statement.executeQuery(
                    "SELECT inventory_state FROM orders WHERE username = 'legacy-user'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("LEGACY_UNTRACKED");
            }
        }
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

    private static boolean columnExists(
            Statement statement,
            String schema,
            String table,
            String column) throws Exception {
        try (ResultSet result = statement.executeQuery("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = '%s' AND table_name = '%s' AND column_name = '%s'
                )
                """.formatted(schema, table, column))) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private static String foreignKeyRule(
            Statement statement,
            String schema,
            String table,
            String column) throws Exception {
        try (ResultSet result = statement.executeQuery("""
                SELECT rc.delete_rule
                FROM information_schema.referential_constraints rc
                JOIN information_schema.key_column_usage kcu
                    ON rc.constraint_name = kcu.constraint_name
                WHERE kcu.table_schema = '%s' AND kcu.table_name = '%s' AND kcu.column_name = '%s'
                """.formatted(schema, table, column))) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
