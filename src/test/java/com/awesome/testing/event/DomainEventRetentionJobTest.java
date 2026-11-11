package com.awesome.testing.event;

import com.awesome.testing.repository.event.DomainEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DomainEventRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final DomainEventRepository repository = mock(DomainEventRepository.class);

    @Test
    void deletesEventsRecordedBeforeTheRetentionWindow() {
        when(repository.deleteRecordedBefore(Instant.parse("2026-10-11T12:00:00Z"))).thenReturn(3);

        int deleted = new DomainEventRetentionJob(repository, CLOCK, Duration.ofDays(30), true).purge();

        assertThat(deleted).isEqualTo(3);
    }

    @Test
    void theScheduleDeletesWhenEnabled() {
        new DomainEventRetentionJob(repository, CLOCK, Duration.ofHours(2), true).scheduledPurge();

        verify(repository).deleteRecordedBefore(Instant.parse("2026-11-10T10:00:00Z"));
    }

    @Test
    void theScheduleDoesNothingWhenDisabled() {
        new DomainEventRetentionJob(repository, CLOCK, Duration.ofDays(30), false).scheduledPurge();

        verifyNoInteractions(repository);
    }

    @Test
    void nothingExpiredDeletesNothing() {
        when(repository.deleteRecordedBefore(any())).thenReturn(0);

        assertThat(new DomainEventRetentionJob(repository, CLOCK, Duration.ofDays(30), true).purge()).isZero();
    }
}
