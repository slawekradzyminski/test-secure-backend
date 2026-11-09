package com.awesome.testing.service.email;

import com.awesome.testing.dto.email.EmailDeliveryStatus;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.EmailEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@Import({EmailEventService.class, EmailEventServiceTest.Json.class})
class EmailEventServiceTest {

    @TestConfiguration
    static class Json {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired private EmailEventService service;
    @Autowired private EmailEventRepository repository;
    @Autowired private TestEntityManager entityManager;

    private UserEntity user;
    private final EmailDto email = EmailDto.builder().to("buyer@example.com").subject("Order #1 has shipped")
            .message("Body").template(EmailTemplate.ORDER_SHIPPED).properties(Map.of("orderId", "1")).build();

    @BeforeEach
    void setUp() {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        user = entityManager.persistAndFlush(UserEntity.builder().username(name).email(name + "@example.com")
                .password("secret-password").build());
    }

    @Test
    void recordsThePayloadDestinationAndRequestId() {
        UUID requestId = UUID.randomUUID();

        Integer id = service.recordQueued(user, email, "email", requestId);

        EmailEventEntity event = repository.findById(id).orElseThrow();
        assertThat(event.getDestination()).isEqualTo("email");
        assertThat(event.getPayload()).contains("Order #1 has shipped").contains("ORDER_SHIPPED");
        assertThat(service.isRecorded(requestId)).isTrue();
        assertThat(service.isRecorded(UUID.randomUUID())).isFalse();
    }

    @Test
    void failuresBackOffExponentiallyAndStopAfterTheLastAttempt() {
        Integer id = service.recordQueued(user, email, "email", null);

        service.markFailed(id, "broker down");
        EmailEventEntity first = repository.findById(id).orElseThrow();
        assertThat(first.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(Duration.between(first.getUpdatedAt(), first.getNextAttemptAt())).isEqualTo(Duration.ofMinutes(1));

        for (int i = 2; i <= EmailEventService.MAX_ATTEMPTS; i++) {
            service.markFailed(id, "broker down");
        }
        EmailEventEntity last = repository.findById(id).orElseThrow();
        assertThat(last.getAttempts()).isEqualTo(EmailEventService.MAX_ATTEMPTS);
        assertThat(last.getNextAttemptAt()).isNull();
    }

    @Test
    void backoffDoublesUpToOneHour() {
        assertThat(EmailEventService.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(EmailEventService.backoff(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(EmailEventService.backoff(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(EmailEventService.backoff(9)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void claimsDueFailuresAndStaleQueuedEmailsOnce() {
        Integer dueFailure = service.recordQueued(user, email, "email", null);
        Integer waitingFailure = service.recordQueued(user, email, "email", null);
        Integer exhausted = service.recordQueued(user, email, "email", null);
        Integer staleQueued = service.recordQueued(user, email, "email", null);
        Integer freshQueued = service.recordQueued(user, email, "email", null);
        Integer sent = service.recordQueued(user, email, "email", null);
        Instant now = Instant.now();
        update(dueFailure, EmailDeliveryStatus.FAILED, 1, now.minusSeconds(1), now);
        update(waitingFailure, EmailDeliveryStatus.FAILED, 1, now.plusSeconds(600), now);
        update(exhausted, EmailDeliveryStatus.FAILED, EmailEventService.MAX_ATTEMPTS, now.minusSeconds(1), now);
        update(staleQueued, EmailDeliveryStatus.QUEUED, 0, null, now.minus(Duration.ofMinutes(21)));
        update(sent, EmailDeliveryStatus.SENT_TO_SMTP_SINK, 0, null, now.minus(Duration.ofHours(2)));

        var claimed = service.claimDue(20);
        var claimedAgain = service.claimDue(20);

        assertThat(claimed).extracting(EmailEventService.Redelivery::eventId).containsExactly(dueFailure, staleQueued);
        assertThat(claimed.getFirst().email()).isEqualTo(email);
        assertThat(claimed.getFirst().destination()).isEqualTo("email");
        assertThat(repository.findById(dueFailure).orElseThrow().getStatus()).isEqualTo(EmailDeliveryStatus.QUEUED);
        assertThat(claimedAgain).isEmpty();
        assertThat(freshQueued).isNotNull();
    }

    private void update(Integer id, EmailDeliveryStatus status, int attempts, Instant nextAttemptAt, Instant updatedAt) {
        EmailEventEntity event = repository.findById(id).orElseThrow();
        event.setStatus(status);
        event.setAttempts(attempts);
        event.setNextAttemptAt(nextAttemptAt);
        event.setUpdatedAt(updatedAt);
        repository.saveAndFlush(event);
    }
}
