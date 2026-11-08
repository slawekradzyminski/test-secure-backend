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

class CommerceMigrationIT {

    private static final String UPGRADE_SCHEMA = "commerce_upgrade";
    private static final String FRESH_SCHEMA = "commerce_fresh";
    private static final String FULFILMENT_SCHEMA = "fulfilment_upgrade";

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
    void v7BackfillsOrderAmountsAndWidensTheEnumConstraints() throws Exception {
        flyway(UPGRADE_SCHEMA, MigrationVersion.fromVersion("6")).migrate();
        try (Connection connection = connect(UPGRADE_SCHEMA); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'DELIVERED', 25.50, CURRENT_TIMESTAMP, 'legacy-buyer')
                    """);
            statement.executeUpdate("""
                    INSERT INTO app_user (email, username) VALUES ('legacy@example.test', 'legacy-warehouse')
                    """);
        }

        flyway(UPGRADE_SCHEMA, null).migrate();

        try (Connection connection = connect(UPGRADE_SCHEMA); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(
                    "SELECT subtotal_amount, discount_amount, coupon_code FROM orders WHERE username = 'legacy-buyer'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("25.50");
                assertThat(result.getBigDecimal(2)).isEqualByComparingTo("0");
                assertThat(result.getString(3)).isNull();
            }
            statement.executeUpdate("""
                    INSERT INTO user_entity_roles (user_entity_id, roles)
                    SELECT id, 2 FROM app_user WHERE username = 'legacy-warehouse'
                    """);
            statement.executeUpdate("""
                    INSERT INTO email_event (created_at, recipient_email, status, type, updated_at, user_id)
                    SELECT CURRENT_TIMESTAMP, email, 'QUEUED', 'ORDER_SHIPPED', CURRENT_TIMESTAMP, id
                    FROM app_user WHERE username = 'legacy-warehouse'
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO user_entity_roles (user_entity_id, roles)
                    SELECT id, 3 FROM app_user WHERE username = 'legacy-warehouse'
                    """)).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void v7RejectsDiscountsLargerThanTheSubtotal() throws Exception {
        flyway(FRESH_SCHEMA, null).migrate();
        try (Connection connection = connect(FRESH_SCHEMA); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, subtotal_amount, discount_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'PENDING', 0, 10.00, 10.01, CURRENT_TIMESTAMP, 'greedy')
                    """)).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void v8DefaultsExistingOrdersToNoneAndRejectsUnknownFulfilmentStates() throws Exception {
        flyway(FULFILMENT_SCHEMA, MigrationVersion.fromVersion("7")).migrate();
        try (Connection connection = connect(FULFILMENT_SCHEMA); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'PAID', 10.00, CURRENT_TIMESTAMP, 'paid-before-v8')
                    """);
        }

        flyway(FULFILMENT_SCHEMA, null).migrate();

        try (Connection connection = connect(FULFILMENT_SCHEMA); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(
                    "SELECT fulfilment_state FROM orders WHERE username = 'paid-before-v8'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("NONE");
            }
            statement.executeUpdate("UPDATE orders SET fulfilment_state = 'AWAITING_PICK' WHERE username = 'paid-before-v8'");
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'PENDING', 5.00, CURRENT_TIMESTAMP, 'legacy-writer')
                    """);
            try (ResultSet result = statement.executeQuery(
                    "SELECT fulfilment_state FROM orders WHERE username = 'legacy-writer'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("NONE");
            }
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE orders SET fulfilment_state = 'LOST' WHERE username = 'paid-before-v8'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE orders SET fulfilment_state = NULL WHERE username = 'paid-before-v8'"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void v7NoLongerCreatesWarehouseTables() throws Exception {
        flyway(FRESH_SCHEMA, null).migrate();
        try (Connection connection = connect(FRESH_SCHEMA); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT count(*) FROM information_schema.tables WHERE table_schema = 'commerce_fresh'
                     AND table_name IN ('shipments', 'return_requests', 'return_items', 'suppliers',
                                        'purchase_orders', 'purchase_order_lines', 'purchase_receipts')
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    private static Connection connect(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
