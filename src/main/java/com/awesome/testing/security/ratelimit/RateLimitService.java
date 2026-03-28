package com.awesome.testing.security.ratelimit;

import com.awesome.testing.controller.exception.CustomException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String RATE_LIMIT_MESSAGE = "Too many requests. Please try again later.";
    private static final String ALLOWED_METRIC = "security.rate_limit.allowed";
    private static final String BLOCKED_METRIC = "security.rate_limit.blocked";

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    private volatile Cache<String, WindowState> cache;

    public void check(String endpoint, String keyType, String key, RateLimitProperties.Policy policy) {
        if (!properties.isEnabled() || policy == null || !policy.isActive() || !StringUtils.hasText(key)) {
            return;
        }

        long now = Instant.now(clock).toEpochMilli();
        long windowMs = policy.getWindow().toMillis();
        String cacheKey = endpoint + "|" + keyType + "|" + normalize(key);

        WindowState state = cache().asMap().compute(cacheKey, (ignored, current) -> nextState(current, now, windowMs));
        if (state == null) {
            return;
        }

        if (state.requestCount() > policy.getCapacity()) {
            record(BLOCKED_METRIC, endpoint, keyType);
            throw new CustomException(RATE_LIMIT_MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
        }

        record(ALLOWED_METRIC, endpoint, keyType);
    }

    private Cache<String, WindowState> cache() {
        Cache<String, WindowState> local = cache;
        if (local == null) {
            synchronized (this) {
                local = cache;
                if (local == null) {
                    local = Caffeine.newBuilder()
                            .maximumSize(properties.getCache().getMaximumSize())
                            .expireAfterAccess(properties.getCache().getExpireAfterAccess().toMillis(), TimeUnit.MILLISECONDS)
                            .build();
                    cache = local;
                }
            }
        }
        return local;
    }

    private static WindowState nextState(WindowState current, long now, long windowMs) {
        if (current == null || current.windowStartedAt() + windowMs <= now) {
            return new WindowState(now, 1);
        }
        return new WindowState(current.windowStartedAt(), current.requestCount() + 1);
    }

    private void record(String metricName, String endpoint, String keyType) {
        meterRegistry.ifAvailable(registry -> registry.counter(metricName, "endpoint", endpoint, "key_type", keyType).increment());
    }

    private static String normalize(String key) {
        return Objects.requireNonNullElse(key, "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private record WindowState(long windowStartedAt, int requestCount) {
    }
}
