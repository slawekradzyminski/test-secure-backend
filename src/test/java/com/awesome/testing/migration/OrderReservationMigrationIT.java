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

/** V23: orders get a nullable reservation end; existing orders keep null and never expire. */
class OrderReservationMigrationIT {

    private static final String SCHEMA = "reservation_upgrade";

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
    void v23AddsANullableReservationEndAndAPartialIndexForPendingOrders() throws Exception {
        flyway(MigrationVersion.fromVersion("22")).migrate();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (now(), 'PENDING', 10.00, now(), 'legacy-buyer')""");
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(single(statement, "SELECT count(*) FROM orders WHERE reservation_expires_at IS NULL")).isEqualTo("1");
            assertThat(single(statement, """
                    SELECT data_type || ':' || is_nullable FROM information_schema.columns
                    WHERE table_schema = '%s' AND table_name = 'orders' AND column_name = 'reservation_expires_at'"""
                    .formatted(SCHEMA))).isEqualTo("timestamp with time zone:YES");
            assertThat(single(statement, """
                    SELECT indexdef FROM pg_indexes
                    WHERE schemaname = '%s' AND indexname = 'idx_orders_pending_reservation_expiry'"""
                    .formatted(SCHEMA)))
                    .contains("(reservation_expires_at, id)")
                    .contains("'PENDING'")
                    .contains("reservation_expires_at IS NOT NULL");
            statement.executeUpdate("UPDATE orders SET reservation_expires_at = '2020-01-01T10:30:00.123Z'");
            assertThat(single(statement, "SELECT count(*) FROM orders WHERE reservation_expires_at <= now()")).isEqualTo("1");
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
