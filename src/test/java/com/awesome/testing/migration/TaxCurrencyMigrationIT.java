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

class TaxCurrencyMigrationIT {

    private static final String SCHEMA = "tax_upgrade";

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
    void v13SeedsRatesAndLeavesExistingOrdersWithoutTax() throws Exception {
        flyway(MigrationVersion.fromVersion("10")).migrate();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (created_at, status, total_amount, updated_at, username)
                    VALUES (CURRENT_TIMESTAMP, 'DELIVERED', 25.50, CURRENT_TIMESTAMP, 'before-v13')
                    """);
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT count(*) FROM vat_rates")).isEqualTo(32);
            assertThat(count(statement, "SELECT count(*) FROM vat_rates WHERE rate = 0")).isEqualTo(2);
            assertThat(count(statement, "SELECT count(*) FROM currency_rates")).isEqualTo(5);
            assertThat(decimal(statement, "SELECT rate FROM vat_rates WHERE country_code = 'HU'")).isEqualTo("27.00");
            assertThat(decimal(statement, "SELECT rate FROM vat_rates WHERE country_code = 'PL'")).isEqualTo("23.00");
            assertThat(decimal(statement, "SELECT rate FROM vat_rates WHERE country_code = 'CH'")).isEqualTo("8.10");
            assertThat(decimal(statement, "SELECT rate_to_usd FROM currency_rates WHERE code = 'USD'")).isEqualTo("1.000000");
            try (ResultSet result = statement.executeQuery(
                    "SELECT tax_country, tax_rate, tax_amount, net_amount FROM orders WHERE username = 'before-v13'")) {
                assertThat(result.next()).isTrue();
                for (int column = 1; column <= 4; column++) {
                    assertThat(result.getObject(column)).isNull();
                }
            }

            statement.executeUpdate("""
                    UPDATE orders SET tax_country = 'PL', tax_rate = 23.00, tax_amount = 4.77, net_amount = 20.73
                    WHERE username = 'before-v13'
                    """);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE orders SET tax_amount = NULL WHERE username = 'before-v13'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO vat_rates VALUES ('XX', 'Too much', 50.01, CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO vat_rates VALUES ('xx', 'Lower case', 10, CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO currency_rates VALUES ('XXX', 'Free', 'X', 0, CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            assertThat(count(statement, """
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = 'tax_upgrade' AND table_name = 'order_refunds' AND column_name = 'tax_amount'
                    """)).isEqualTo(1);
        }
    }

    private static int count(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static String decimal(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getBigDecimal(1).toPlainString();
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
