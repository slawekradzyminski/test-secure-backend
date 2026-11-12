package com.awesome.testing.traffic;

import com.awesome.testing.repository.TrafficLogRepository;
import com.awesome.testing.repository.TrafficSessionOwnerRepository;
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
    private final TrafficSessionOwnerRepository sessionOwnerRepository;
    private final TrafficSessionOwners sessionOwners;

    public TrafficLogRetentionService(TrafficLogRepository trafficLogRepository,
                                      TrafficProperties trafficProperties,
                                      @Qualifier("rateLimitClock") Clock clock,
                                      TrafficSessionOwnerRepository sessionOwnerRepository,
                                      TrafficSessionOwners sessionOwners) {
        this.trafficLogRepository = trafficLogRepository;
        this.sessionOwnerRepository = sessionOwnerRepository;
        this.sessionOwners = sessionOwners;
        this.trafficProperties = trafficProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.traffic.cleanup-interval:PT24H}")
    public void cleanupExpiredLogs() {
        Instant cutoff = Instant.now(clock).minus(trafficProperties.getRetention());
        long deleted = trafficLogRepository.deleteByTimestampBefore(cutoff);
        log.debug("Deleted {} expired traffic log entries older than {}", deleted, cutoff);
        if (sessionOwnerRepository.deleteUnusedBoundBefore(cutoff) > 0) {
            sessionOwners.evictAll();
        }
    }
}
