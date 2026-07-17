package com.awesome.testing.repository;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.PasswordResetTokenEntity;
import com.awesome.testing.entity.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PasswordResetTokenConcurrencyIT {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldSerializeConcurrentTokenConsumption() throws Exception {
        createToken();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> transaction.execute(status -> {
                PasswordResetTokenEntity token = tokenRepository.findByTokenHashForUpdate("token-hash")
                        .orElseThrow();
                firstHasLock.countDown();
                await(releaseFirst);
                return consume(token);
            }));

            assertThat(firstHasLock.await(5, SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() -> {
                secondAttempted.countDown();
                return transaction.execute(status -> tokenRepository.findByTokenHashForUpdate("token-hash")
                        .map(this::consume)
                        .orElse(false));
            });

            assertThat(secondAttempted.await(5, SECONDS)).isTrue();
            try {
                Thread.sleep(200);
                assertThat(second).isNotDone();
            } finally {
                releaseFirst.countDown();
            }

            assertThat(List.of(first.get(5, SECONDS), second.get(5, SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    private void createToken() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            UserEntity user = userRepository.save(UserEntity.builder()
                    .username("reset-user")
                    .email("reset@example.com")
                    .password("encoded-password")
                    .roles(List.of(Role.ROLE_CLIENT))
                    .build());
            tokenRepository.save(PasswordResetTokenEntity.builder()
                    .tokenHash("token-hash")
                    .requestedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .user(user)
                    .build());
        });
    }

    private boolean consume(PasswordResetTokenEntity token) {
        if (token.isConsumed()) {
            return false;
        }
        token.setConsumedAt(Instant.now());
        tokenRepository.saveAndFlush(token);
        return true;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent reset-token test");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while testing reset-token locking", ex);
        }
    }
}
