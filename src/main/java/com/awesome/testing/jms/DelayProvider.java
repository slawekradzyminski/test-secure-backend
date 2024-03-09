package com.awesome.testing.jms;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

@Component
public class DelayProvider {

    public long getRandomDelayInSeconds() {
        return ThreadLocalRandom.current()
                .nextLong(1, TimeUnit.MINUTES.toSeconds(10));
    }

}
