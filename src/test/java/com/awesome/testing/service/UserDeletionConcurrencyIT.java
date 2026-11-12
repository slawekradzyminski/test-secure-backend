package com.awesome.testing.service;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.email.EmailEventService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a user while notification emails for them are recorded (carrier and warehouse notifications
 * arrive at any time) on PostgreSQL row locks. Without the user row lock an email event committed between
 * the deletion of the user's email events and of the user made the delete fail on email_event_user_id_fkey.
 */
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
class UserDeletionConcurrencyIT extends DomainHelper {

    private static final int USERS = 20;
    private static final int MAX_EVENTS_PER_USER = 2_000;
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine@sha256:"
                    + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private UserService userService;
    @Autowired
    private EmailEventService emailEventService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void deletingAUserWhileEmailEventsAreRecordedForThemAlwaysSucceeds() throws Exception {
        List<Outcome> outcomes = new ArrayList<>();
        for (int round = 0; round < USERS; round++) {
            // given
            UserEntity user = user();

            // when
            outcomes.add(deleteWhileRecording(user));

            // then
            assertThat(userRepository.findByUsername(user.getUsername())).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM email_event WHERE user_id = ?",
                    Long.class, user.getId())).isZero();
        }
        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.deleteFailure()).isNull();
            assertThat(outcome.recordFailure()).isNull();
        });
        assertThat(outcomes).anySatisfy(outcome -> assertThat(outcome.recordedBeforeDelete()).isPositive());
    }

    private Outcome deleteWhileRecording(UserEntity user) throws Exception {
        CountDownLatch firstRecorded = new CountDownLatch(1);
        EmailDto email = EmailDto.builder().to(user.getEmail()).subject("Your parcel is on its way").message("Body")
                .template(EmailTemplate.GENERIC).build();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<RecordResult> recorder = executor.submit(() -> {
                int recorded = 0;
                try {
                    for (int attempt = 0; attempt < MAX_EVENTS_PER_USER; attempt++) {
                        if (emailEventService.recordQueued(user, email, "email", null) == null) {
                            return new RecordResult(recorded, null);
                        }
                        recorded++;
                        firstRecorded.countDown();
                    }
                    return new RecordResult(recorded, null);
                } catch (RuntimeException e) {
                    return new RecordResult(recorded, e);
                } finally {
                    firstRecorded.countDown();
                }
            });
            assertThat(firstRecorded.await(10, SECONDS)).isTrue();
            Throwable deleteFailure = null;
            try {
                userService.delete(user.getUsername());
            } catch (RuntimeException e) {
                deleteFailure = e;
            }
            RecordResult result = recorder.get(30, SECONDS);
            return new Outcome(result.recorded(), deleteFailure, result.failure());
        }
    }

    private UserEntity user() {
        String name = "del-" + UUID.randomUUID().toString().substring(0, 12);
        return userRepository.saveAndFlush(UserEntity.builder().username(name).email(name + "@example.com")
                .password("secret-password").firstName("Del").lastName("Ete").build());
    }

    private record RecordResult(int recorded, Throwable failure) {
    }

    private record Outcome(int recordedBeforeDelete, Throwable deleteFailure, Throwable recordFailure) {
    }
}
