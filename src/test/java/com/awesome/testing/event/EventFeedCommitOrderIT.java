package com.awesome.testing.event;

import com.awesome.testing.config.CommerceClockConfig;
import com.awesome.testing.dto.event.EventFeedDto;
import com.awesome.testing.dto.event.FeedEventDto;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The feed on PostgreSQL never moves a consumer's cursor past an event whose transaction commits
 * later (exploratory chaos session, finding F1). A slow cancellation inserted its events first,
 * a faster one inserted later but committed first, and a consumer that polled in between used to
 * skip the slow cancellation's events for good: its {@code card-refund.requested} was never served,
 * so the customer was not refunded.
 */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(CommerceClockConfig.class)
@ComponentScan(basePackageClasses = EventFeedService.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.event\\.(EventFeedService|FeedPositions)"))
class EventFeedCommitOrderIT {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:16-alpine@sha256:"
                                            + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                            .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired private EventFeedService feed;
    @Autowired private DomainEventRepository events;
    @Autowired private PlatformTransactionManager transactionManager;

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
    void anEventThatCommitsAfterALaterSequenceIsStillServed() throws Exception {
        long cursor = feed.read(null, "200", null).nextCursor();
        CountDownLatch slowInserted = new CountDownLatch(1);
        CountDownLatch commitSlow = new CountDownLatch(1);
        UUID slowRefund = UUID.randomUUID();
        UUID fastRefund = UUID.randomUUID();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // The slow cancellation inserts its refund event (lower sequence) and stalls before commit.
            Future<?> slow = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                events.saveAndFlush(refundEvent(slowRefund, 165L));
                slowInserted.countDown();
                await(commitSlow);
            }));
            assertThat(slowInserted.await(5, SECONDS)).isTrue();

            // Another cancellation records its refund event (higher sequence) and commits first.
            new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                    events.saveAndFlush(refundEvent(fastRefund, 53L)));

            // payment-service polls meanwhile and moves its cursor.
            List<FeedEventDto> served = new ArrayList<>();
            EventFeedDto first = feed.read(Long.toString(cursor), "200", "card-refund.requested");
            served.addAll(first.events());
            cursor = first.nextCursor();
            assertThat(served).extracting(FeedEventDto::id).containsExactly(fastRefund);

            commitSlow.countDown();
            slow.get(5, SECONDS);

            EventFeedDto second = feed.read(Long.toString(cursor), "200", "card-refund.requested");
            served.addAll(second.events());
            assertThat(served).extracting(FeedEventDto::id)
                    .as("the slow cancellation's refund is served after it commits")
                    .containsExactly(fastRefund, slowRefund);
            assertThat(second.nextCursor()).isGreaterThan(cursor);
        }
    }

    private static DomainEventEntity refundEvent(UUID eventId, long orderId) {
        Instant now = Instant.now();
        return DomainEventEntity.builder()
                .eventId(eventId)
                .type("card-refund.requested")
                .orderId(orderId)
                .username("client")
                .occurredAt(now)
                .recordedAt(now)
                .payload("{\"amount\":\"399.99\",\"orderId\":" + orderId + "}")
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
