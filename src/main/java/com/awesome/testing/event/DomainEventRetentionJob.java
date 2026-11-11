package com.awesome.testing.event;

import com.awesome.testing.repository.event.DomainEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Deletes feed events recorded more than {@code events.retention} ago. Consumers are expected
 * to keep up well within that window. {@code events.retention-job.enabled} switches the
 * schedule off (tests call {@link #purge()} directly).
 */
@Slf4j
@Component
public class DomainEventRetentionJob {

    private final DomainEventRepository repository;
    private final Clock commerceClock;
    private final Duration retention;
    private final boolean enabled;

    public DomainEventRetentionJob(DomainEventRepository repository, Clock commerceClock,
                                   @Value("${events.retention:P30D}") Duration retention,
                                   @Value("${events.retention-job.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.commerceClock = commerceClock;
        this.retention = retention;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${events.retention-job.interval:PT1H}",
            initialDelayString = "${events.retention-job.initial-delay:PT5M}")
    public void scheduledPurge() {
        if (enabled) {
            purge();
        }
    }

    /** Deletes expired events now; returns how many were deleted. */
    @Transactional
    public int purge() {
        int deleted = repository.deleteRecordedBefore(commerceClock.instant().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} domain event(s) older than {}", deleted, retention);
        }
        return deleted;
    }
}
