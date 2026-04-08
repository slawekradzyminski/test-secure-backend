package com.awesome.testing.traffic;

import com.awesome.testing.repository.TrafficLogRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TrafficLogRetentionService {

    private final TrafficLogRepository trafficLogRepository;
    private final TrafficProperties trafficProperties;
    private final Clock clock;

    public TrafficLogRetentionService(TrafficLogRepository trafficLogRepository,
                                      TrafficProperties trafficProperties,
                                      @Qualifier("rateLimitClock") Clock clock) {
        this.trafficLogRepository = trafficLogRepository;
        this.trafficProperties = trafficProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.traffic.cleanup-interval:PT24H}")
    public void cleanupExpiredLogs() {
        Instant cutoff = Instant.now(clock).minus(trafficProperties.getRetention());
        long deleted = trafficLogRepository.deleteByTimestampBefore(cutoff);
        log.debug("Deleted {} expired traffic log entries older than {}", deleted, cutoff);
    }
}
