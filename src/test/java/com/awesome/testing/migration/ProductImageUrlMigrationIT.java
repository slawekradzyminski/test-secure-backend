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

/** V19 (catalog X-01): products without an image store '' instead of null. */
class ProductImageUrlMigrationIT {

    private static final String SCHEMA = "product_image_upgrade";

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
    void v19ConvertsNullImageUrlsAndRejectsNewOnes() throws Exception {
        flyway(MigrationVersion.fromVersion("18")).migrate();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(product("'without-image'", "NULL"));
            statement.executeUpdate(product("'with-image'", "'/images/a.png'"));
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(single(statement, "SELECT image_url FROM products WHERE name = 'without-image'")).isEmpty();
            assertThat(single(statement, "SELECT image_url FROM products WHERE name = 'with-image'"))
                    .isEqualTo("/images/a.png");
            statement.executeUpdate("""
                    INSERT INTO products (name, description, price, stock_quantity, category, version, created_at, updated_at)
                    VALUES ('defaulted', 'd', 1, 1, 'c', 0, now(), now())""");
            assertThat(single(statement, "SELECT image_url FROM products WHERE name = 'defaulted'")).isEmpty();
            assertThatThrownBy(() -> statement.executeUpdate(product("'null-again'", "NULL")))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }

    private static String product(String name, String imageUrl) {
        return """
                INSERT INTO products (name, description, price, stock_quantity, category, version, created_at,
                                      updated_at, image_url)
                VALUES (%s, 'd', 1, 1, 'c', 0, now(), now(), %s)""".formatted(name, imageUrl);
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
