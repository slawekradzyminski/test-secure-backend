package com.awesome.testing.service.delay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RandomDelayGeneratorTest {

    @Test
    void shouldGenerateValueWithinExpectedRange() {
        RandomDelayGenerator generator = new RandomDelayGenerator();
        for (int i = 0; i < 50; i++) {
            long delay = generator.getDelayMillis();
            assertThat(delay).isGreaterThanOrEqualTo(10_000L);
            assertThat(delay).isLessThanOrEqualTo(600_000L);
        }
    }
}
