package com.awesome.testing.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.JwtAuthServiceApp;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.UserRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The training profiles that used to run on H2 now boot on PostgreSQL: Flyway applies the
 * production migrations, Hibernate validates the entities against them and demo data seeds.
 */
class RuntimeProfilesPostgresIT {
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:16-alpine@sha256:"
                                            + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                            .asCompatibleSubstituteFor("postgres"));

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "aitesters"})
    void profileMigratesValidatesAndSeedsOnPostgres(String profile) {
        cleanDatabase();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(JwtAuthServiceApp.class)
                .run(
                        // Replaces the test profile that src/test/resources activates by default.
                        "--spring.profiles.active=" + profile,
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--server.port=0",
                        "--spring.grpc.server.enabled=false",
                        "--password-reset.outbox-access-key=runtime-profile-test-outbox-key")) {
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(context.getBean(Flyway.class).info().applied()).isNotEmpty();
            assertThat(context.getBean(ProductRepository.class).count()).isPositive();
            assertThat(context.getBean(UserRepository.class).count()).isPositive();
        }
    }

    private static void cleanDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
    }
}
