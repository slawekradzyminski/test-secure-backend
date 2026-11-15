package com.awesome.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NettyAllocatorTest {

    private static final String ALLOCATOR = "io.netty.allocator.type";
    private final String before = System.getProperty(ALLOCATOR);

    @AfterEach
    void restore() {
        if (before == null) {
            System.clearProperty(ALLOCATOR);
        } else {
            System.setProperty(ALLOCATOR, before);
        }
    }

    @Test
    void thePooledAllocatorIsChosenWhenNothingIsSet() {
        System.clearProperty(ALLOCATOR);

        JwtAuthServiceApp.usePooledNettyAllocator();

        assertThat(System.getProperty(ALLOCATOR)).isEqualTo("pooled");
    }

    @Test
    void anExplicitAllocatorWins() {
        System.setProperty(ALLOCATOR, "adaptive");

        JwtAuthServiceApp.usePooledNettyAllocator();

        assertThat(System.getProperty(ALLOCATOR)).isEqualTo("adaptive");
    }
}
