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

/** V12 adds gift cards, loyalty points, cart tenders and the order tender columns. */
class LoyaltyGiftCardMigrationIT {


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
    void v12KeepsExistingOrdersUntenderedAndEnforcesTheTenderRules() throws Exception {
        String schema = "loyalty_upgrade";
        flyway(schema, MigrationVersion.fromVersion("10")).migrate();
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'PAID', 40.00, CURRENT_TIMESTAMP, 'before-v12')
                    """);
        }

        flyway(schema, null).migrate();

        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                    SELECT gift_card_code, gift_card_amount, points_redeemed, points_amount
                    FROM orders WHERE username = 'before-v12'
                    """)) {
                assertThat(result.next()).isTrue();
                for (int column = 1; column <= 4; column++) {
                    assertThat(result.getObject(column)).isNull();
                }
            }
            statement.executeUpdate("""
                    UPDATE orders SET gift_card_code = 'GC-AAAA-BBBB-CCCC', gift_card_amount = 15.00,
                        points_redeemed = 1500, points_amount = 15.00 WHERE username = 'before-v12'
                    """);
            assertRejected(statement, "UPDATE orders SET gift_card_amount = 30.00 WHERE username = 'before-v12'");
            assertRejected(statement, "UPDATE orders SET points_amount = 14.00 WHERE username = 'before-v12'");
            assertRejected(statement, "UPDATE orders SET gift_card_code = NULL WHERE username = 'before-v12'");
            assertRejected(statement, "UPDATE orders SET points_redeemed = 0, points_amount = 0 WHERE username = 'before-v12'");
        }
    }

    @Test
    void v12ConstrainsGiftCardsLedgersAndCartTenders() throws Exception {
        String schema = "loyalty_constraints";
        flyway(schema, null).migrate();
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO gift_cards (code, initial_amount, balance, created_by, created_at, updated_at)
                    VALUES ('GC-MIGR-ATE1-TEST', 50.00, 20.00, 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertRejected(statement, giftCard("GC-MIGR-ATE1-TEST", "10.00", "10.00"));
            assertRejected(statement, giftCard("gc-lower-case-code", "10.00", "10.00"));
            assertRejected(statement, giftCard("GC-OVER-LIMI-T000", "1000.01", "1000.01"));
            assertRejected(statement, giftCard("GC-MORE-THAN-INIT", "10.00", "10.01"));
            assertRejected(statement, giftCard("GC-NEGA-TIVE-BALA", "10.00", "-0.01"));

            statement.executeUpdate("""
                    INSERT INTO gift_card_transactions (gift_card_id, type, amount, balance_after, reference, created_at)
                    SELECT id, 'REDEEMED', -30.00, 20.00, 'order:1', CURRENT_TIMESTAMP FROM gift_cards
                    """);
            assertRejected(statement, """
                    INSERT INTO gift_card_transactions (gift_card_id, type, amount, balance_after, reference, created_at)
                    SELECT id, 'REDEEMED', -30.00, 20.00, 'order:1', CURRENT_TIMESTAMP FROM gift_cards
                    """);
            assertRejected(statement, """
                    INSERT INTO gift_card_transactions (gift_card_id, type, amount, balance_after, created_at)
                    SELECT id, 'SPENT', -1.00, 19.00, CURRENT_TIMESTAMP FROM gift_cards
                    """);

            statement.executeUpdate("""
                    INSERT INTO loyalty_accounts (username, balance, lifetime_earned, created_at, updated_at)
                    VALUES ('points-user', 100, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertRejected(statement, "UPDATE loyalty_accounts SET balance = -1 WHERE username = 'points-user'");
            statement.executeUpdate("""
                    INSERT INTO loyalty_transactions (username, type, points, balance_after, reference, created_at)
                    VALUES ('points-user', 'EARNED', 100, 100, 'order:7', CURRENT_TIMESTAMP)
                    """);
            assertRejected(statement, """
                    INSERT INTO loyalty_transactions (username, type, points, balance_after, reference, created_at)
                    VALUES ('points-user', 'EARNED', 100, 200, 'order:7', CURRENT_TIMESTAMP)
                    """);
            assertRejected(statement, """
                    INSERT INTO loyalty_transactions (username, type, points, balance_after, created_at)
                    VALUES ('ghost', 'EARNED', 100, 100, CURRENT_TIMESTAMP)
                    """);

            statement.executeUpdate("""
                    INSERT INTO cart_tenders (username, gift_card_code, points, updated_at)
                    VALUES ('points-user', 'GC-MIGR-ATE1-TEST', 200, CURRENT_TIMESTAMP)
                    """);
            assertRejected(statement, "UPDATE cart_tenders SET points = 150 WHERE username = 'points-user'");
            assertRejected(statement, "UPDATE cart_tenders SET points = 0 WHERE username = 'points-user'");

            statement.executeUpdate("DELETE FROM loyalty_accounts WHERE username = 'points-user'");
            try (ResultSet result = statement.executeQuery("SELECT count(*) FROM loyalty_transactions")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }

    @Test
    void deletingAnOrderKeepsTheLedgerWithoutItsOrder() throws Exception {
        String schema = "loyalty_order_delete";
        flyway(schema, null).migrate();
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'PAID', 10.00, CURRENT_TIMESTAMP, 'ledger-owner')
                    """);
            statement.executeUpdate(giftCard("GC-LEDG-ER00-KEEP", "10.00", "10.00"));
            statement.executeUpdate("""
                    INSERT INTO gift_card_transactions (gift_card_id, type, amount, balance_after, order_id, created_at)
                    SELECT g.id, 'REDEEMED', -5.00, 5.00, o.id, CURRENT_TIMESTAMP
                    FROM gift_cards g, orders o WHERE g.code = 'GC-LEDG-ER00-KEEP' AND o.username = 'ledger-owner'
                    """);

            statement.executeUpdate("DELETE FROM orders WHERE username = 'ledger-owner'");

            try (ResultSet result = statement.executeQuery("""
                    SELECT t.order_id FROM gift_card_transactions t JOIN gift_cards g ON g.id = t.gift_card_id
                    WHERE g.code = 'GC-LEDG-ER00-KEEP'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
            }
        }
    }

    private static String giftCard(String code, String initial, String balance) {
        return """
                INSERT INTO gift_cards (code, initial_amount, balance, created_by, created_at, updated_at)
                VALUES ('%s', %s, %s, 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(code, initial, balance);
    }

    private static void assertRejected(Statement statement, String sql) {
        assertThatThrownBy(() -> statement.executeUpdate(sql)).as(sql).isInstanceOf(SQLException.class);
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
