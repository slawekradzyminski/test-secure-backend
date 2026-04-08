package com.awesome.testing.traffic;

import com.awesome.testing.repository.TrafficLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TrafficLogRetentionServiceTest {

    @Test
    void shouldDeleteRecordsOlderThanConfiguredRetention() {
        TrafficProperties properties = new TrafficProperties();
        properties.setRetention(Duration.ofDays(1));
        TrafficLogRepository repository = mock(TrafficLogRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-08T10:15:30Z"), ZoneOffset.UTC);

        TrafficLogRetentionService service = new TrafficLogRetentionService(repository, properties, clock);
        service.cleanupExpiredLogs();

        verify(repository).deleteByTimestampBefore(Instant.parse("2026-04-07T10:15:30Z"));
    }
}
