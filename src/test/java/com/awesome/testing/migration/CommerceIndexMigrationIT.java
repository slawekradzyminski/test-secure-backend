package com.awesome.testing.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;

class CommerceIndexMigrationIT {
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:16-alpine@sha256:"
                                            + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                            .asCompatibleSubstituteFor("postgres"));
    private static long legacyObject;

    @BeforeAll
    static void prepareDatabase() throws Exception {
        POSTGRES.start();
        migrate(MigrationVersion.fromVersion("4"));
        legacyObject = insertLegacyBody();
        migrate(MigrationVersion.LATEST);
        insertOrders();
        insertProducts();
        insertTraffic();
        execute("ANALYZE");
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void upgradePreservesRowsAndSearchablePayloadsWithoutOrphaningLargeObjects() throws Exception {
        assertThat(scalar("SELECT count(*) FROM orders")).isEqualTo("20000");
        assertThat(scalar("SELECT response_body FROM traffic_log WHERE lower(response_body) LIKE '%retained payload%'"))
                .isEqualTo("{\"message\":\"retained payload\"}");
        assertThat(scalar("SELECT count(*) FROM pg_largeobject_metadata WHERE oid = " + legacyObject)).isEqualTo("0");
    }

    @Test
    void selectiveCommerceAndTrafficQueriesUseTheNewIndexes() throws Exception {
        assertThat(
                        plan(
                                "SELECT id FROM orders WHERE username = 'customer-42' AND status = 'PAID' ORDER BY id LIMIT 20"))
                .contains("idx_orders_username_status_id");
        assertThat(plan("SELECT id FROM products WHERE lower(category) = 'rare' ORDER BY id LIMIT 20"))
                .contains("idx_products_category_id");
        assertThat(
                        plan(
                                "SELECT id FROM traffic_log WHERE client_session_id = 'session-42' ORDER BY \"timestamp\" DESC, id DESC LIMIT 20"))
                .contains("idx_traffic_log_session_time");
    }

    private static void migrate(MigrationVersion version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(version)
                .load()
                .migrate();
    }

    private static long insertLegacyBody() throws Exception {
        execute(
                """
                INSERT INTO traffic_log(correlation_id, client_session_id, "timestamp", duration_ms,
                   method, path, status, response_body)
                VALUES ('legacy-body', 'legacy-session', CURRENT_TIMESTAMP, 1, 'GET', '/legacy', 200,
                   lo_from_bytea(0, convert_to('{"message":"retained payload"}', 'UTF8')))
                """);
        return Long.parseLong(scalar("SELECT response_body FROM traffic_log WHERE correlation_id = 'legacy-body'"));
    }

    private static void insertOrders() throws Exception {
        execute(
                """
                INSERT INTO orders(created_at, updated_at, username, status, total_amount)
                SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'customer-' || (n % 100),
                       CASE WHEN n % 2 = 0 THEN 'PAID' ELSE 'PENDING' END, 10.00
                FROM generate_series(1, 20000) n
                """);
    }

    private static void insertProducts() throws Exception {
        execute(
                """
                INSERT INTO products(name, description, price, stock_quantity, category, created_at, updated_at)
                SELECT 'Product ' || n, 'Fixture', 10.00, 5,
                       CASE WHEN n < 100 THEN 'Rare' ELSE 'Bulk' END,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM generate_series(1, 20000) n
                """);
    }

    private static void insertTraffic() throws Exception {
        execute(
                """
                INSERT INTO traffic_log(correlation_id, client_session_id, "timestamp", duration_ms, method, path,
                  status, request_body_truncated, request_body_original_length, request_body_stored_length,
                  response_body_truncated, response_body_original_length, response_body_stored_length)
                SELECT 'correlation-' || n, 'session-' || (n % 100), CURRENT_TIMESTAMP, 1, 'GET', '/products',
                       200, false, 0, 0, false, 0, 0 FROM generate_series(1, 20000) n
                """);
    }

    private static String plan(String sql) throws Exception {
        return scalar("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql);
    }

    private static String scalar(String sql) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static void execute(String sql) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
