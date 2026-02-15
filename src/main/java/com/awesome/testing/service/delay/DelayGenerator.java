package com.awesome.testing.service.delay;

@FunctionalInterface
public interface DelayGenerator {
    long getDelayMillis();
} 
