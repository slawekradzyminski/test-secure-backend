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
 * V25: the stock ledger moves to warehouse-service. Products get the projection's ledger version
 * (0 for existing rows), orders an internal reservation id, and the stock command outbox appears.
 */
class StockLedgerProjectionMigrationIT {

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
    void v25AddsTheProjectionVersionTheReservationIdAndTheOutboxToAnExistingShop() throws Exception {
        String schema = "stock_upgrade";
        flyway(schema, MigrationVersion.fromVersion("24"), false).migrate();
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO products (category, created_at, description, name, price, stock_quantity, updated_at,
                                          version, image_url)
                    VALUES ('Tests', now(), 'Existing', 'Existing product', 10.00, 7, now(), 0, '')""");
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (now(), 'PENDING', 10.00, now(), 'legacy-buyer')""");
        }

        flyway(schema, null, false).migrate();

        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            assertThat(single(statement, "SELECT stock_quantity || ':' || stock_ledger_version FROM products"))
                    .isEqualTo("7:0");
            assertThat(single(statement, "SELECT count(*) FROM orders WHERE stock_reservation_id IS NULL")).isEqualTo("1");
            assertThat(column(statement, schema, "products", "stock_ledger_version")).isEqualTo("bigint:NO");
            assertThat(column(statement, schema, "orders", "stock_reservation_id")).isEqualTo("uuid:YES");
            assertThat(single(statement, """
                    SELECT indexdef FROM pg_indexes WHERE schemaname = '%s' AND indexname = 'uk_orders_stock_reservation'"""
                    .formatted(schema))).contains("UNIQUE").contains("stock_reservation_id IS NOT NULL");
            statement.executeUpdate("""
                    INSERT INTO stock_command_outbox (command, product_id, initial_quantity, actor, request_id, status,
                                                      next_attempt_at, created_at)
                    VALUES ('INITIAL_STOCK', 1, 7, 'admin', gen_random_uuid(), 'PENDING', now(), now())""");
            assertThat(single(statement, "SELECT attempts FROM stock_command_outbox")).isEqualTo("0");
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO stock_command_outbox (command, product_id, request_id, status, next_attempt_at, created_at)
                    VALUES ('RESTOCK', 1, gen_random_uuid(), 'PENDING', now(), now())""")).isInstanceOf(SQLException.class);
        }
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO stock_command_outbox (command, product_id, initial_quantity, request_id, status,
                                                      next_attempt_at, created_at)
                    VALUES ('INITIAL_STOCK', 1, -1, gen_random_uuid(), 'PENDING', now(), now())""")).isInstanceOf(SQLException.class);
        }
        assertThat(flyway(schema, null, false).migrate().migrationsExecuted).isZero();
    }

    @Test
    void v25SkipsTablesALegacySchemaBaselinedAtV1DoesNotHave() throws Exception {
        String schema = "stock_legacy";
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            // A legacy production schema: users and refresh tokens, no commerce tables.
            statement.execute("""
                    CREATE TABLE %1$s.app_user (
                        id integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        email varchar(255) NOT NULL UNIQUE,
                        username varchar(255) NOT NULL UNIQUE
                    );
                    CREATE TABLE %1$s.refresh_token (
                        id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        expires_at timestamptz NOT NULL,
                        revoked boolean NOT NULL,
                        token varchar(255) NOT NULL UNIQUE,
                        user_id integer NOT NULL REFERENCES %1$s.app_user(id)
                    );
                    """.formatted(schema));
        }

        flyway(schema, null, true).migrate();

        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            assertThat(single(statement, "SELECT count(*) FROM stock_command_outbox")).isEqualTo("0");
            assertThat(single(statement, """
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = '%s' AND column_name IN ('stock_ledger_version', 'stock_reservation_id')"""
                    .formatted(schema))).isEqualTo("0");
        }
    }

    private static String column(Statement statement, String schema, String table, String name) throws SQLException {
        return single(statement, """
                SELECT data_type || ':' || is_nullable FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = '%s' AND column_name = '%s'""".formatted(schema, table, name));
    }

    private static String single(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            String value = result.getString(1);
            assertThat(result.next()).isFalse();
            return value;
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

    private static Flyway flyway(String schema, MigrationVersion target, boolean baselineOnMigrate) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .createSchemas(true)
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("1");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
