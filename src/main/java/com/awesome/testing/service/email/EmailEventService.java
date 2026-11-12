package com.awesome.testing.service.email;

import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.dto.email.EmailDeliveryStatus;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailEventDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.EmailEventRepository;
import com.awesome.testing.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Email events record every email sent to a known user together with its wire
 * payload, so failed sends (and sends lost when the backend restarted during the
 * training delay) can be retried by {@link EmailRetryJob}.
 */
@Service
@RequiredArgsConstructor
public class EmailEventService {

    /** Failed sends are retried this many times in total before the event stays FAILED. */
    static final int MAX_ATTEMPTS = 5;
    /** Longer than the longest training delay (10 minutes), so in-flight sends are never taken for lost. */
    static final Duration STALE_QUEUED_AFTER = Duration.ofMinutes(20);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final EmailEventRepository emailEventRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock emailClock = Clock.systemUTC();

    /** A claimed event to send again, with the correlation ids of the request that queued it. */
    public record Redelivery(Integer eventId, EmailDto email, String destination, String correlationRequestId,
                             String traceparent) {
    }

    /**
     * Records a queued email for {@code user}, share-locking the user row so a concurrent deletion either
     * waits for this insert (and then deletes the event with the user) or finishes first.
     *
     * @return the event id, or {@code null} when the user has been deleted and nothing was recorded
     */
    @Transactional
    public Integer recordQueued(UserEntity user, EmailDto emailDto, String destination, UUID requestId) {
        Optional<UserEntity> owner = userRepository.findByIdForShare(user.getId());
        if (owner.isEmpty()) {
            return null;
        }
        Instant now = Instant.now(emailClock);
        EmailEventEntity event = EmailEventEntity.builder()
                .user(owner.get())
                .type(resolveType(emailDto))
                .status(EmailDeliveryStatus.QUEUED)
                .recipientEmail(emailDto.getTo())
                .destination(destination)
                .payload(objectMapper.writeValueAsString(emailDto))
                .requestId(requestId)
                .correlationRequestId(CorrelationIds.currentRequestId())
                .traceparent(CorrelationIds.currentTraceparent())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return emailEventRepository.save(event).getId();
    }

    @Transactional(readOnly = true)
    public boolean isRecorded(UUID requestId) {
        return emailEventRepository.existsByRequestId(requestId);
    }

    @Transactional
    public void markSentToSink(Integer eventId) {
        emailEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(EmailDeliveryStatus.SENT_TO_SMTP_SINK);
            event.setFailureReason(null);
            event.setNextAttemptAt(null);
            event.setUpdatedAt(Instant.now(emailClock));
            emailEventRepository.save(event);
        });
    }

    /** Records a failed send and schedules the next attempt with exponential backoff. */
    @Transactional
    public void markFailed(Integer eventId, String failureReason) {
        emailEventRepository.findById(eventId).ifPresent(event -> {
            Instant now = Instant.now(emailClock);
            int attempts = event.getAttempts() + 1;
            event.setStatus(EmailDeliveryStatus.FAILED);
            event.setFailureReason(truncate(failureReason));
            event.setAttempts(attempts);
            event.setNextAttemptAt(attempts >= MAX_ATTEMPTS ? null : now.plus(backoff(attempts)));
            event.setUpdatedAt(now);
            emailEventRepository.save(event);
        });
    }

    /**
     * Claims up to {@code limit} events that are due for another send: failed ones whose
     * backoff has passed and queued ones that were never sent (lost in a restart). Claimed
     * events go back to QUEUED with a fresh timestamp, so they are not claimed twice.
     */
    @Transactional
    public List<Redelivery> claimDue(int limit) {
        Instant now = Instant.now(emailClock);
        List<EmailEventEntity> due = emailEventRepository.findDueForRetry(
                EmailDeliveryStatus.FAILED, MAX_ATTEMPTS, now,
                EmailDeliveryStatus.QUEUED, now.minus(STALE_QUEUED_AFTER),
                PageRequest.of(0, limit));
        return due.stream().map(event -> {
            event.setStatus(EmailDeliveryStatus.QUEUED);
            event.setNextAttemptAt(null);
            event.setUpdatedAt(now);
            emailEventRepository.save(event);
            return new Redelivery(event.getId(), objectMapper.readValue(event.getPayload(), EmailDto.class),
                    event.getDestination(), event.getCorrelationRequestId(), event.getTraceparent());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<EmailEventDto> getLatestEventsFor(UserEntity user) {
        return emailEventRepository.findTop20ByUserOrderByCreatedAtDesc(user).stream()
                .map(EmailEventDto::from)
                .toList();
    }

    static Duration backoff(int attempts) {
        Duration delay = Duration.ofMinutes(1L << Math.min(attempts - 1, 10));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static EmailTemplate resolveType(EmailDto emailDto) {
        return emailDto.getTemplate() == null ? EmailTemplate.GENERIC : emailDto.getTemplate();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
