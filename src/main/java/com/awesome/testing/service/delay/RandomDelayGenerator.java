package com.awesome.testing.service.delay;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
public class RandomDelayGenerator implements DelayGenerator {
    private static final long MIN_DELAY = TimeUnit.SECONDS.toMillis(10);
    private static final long MAX_DELAY = TimeUnit.MINUTES.toMillis(10);
    private final Random random = new Random();

    @Override
    public long getDelayMillis() {
        return MIN_DELAY + (long) (random.nextDouble() * (MAX_DELAY - MIN_DELAY));
    }
} 