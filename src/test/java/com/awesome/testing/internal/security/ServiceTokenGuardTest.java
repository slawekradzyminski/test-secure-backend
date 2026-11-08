package com.awesome.testing.internal.security;

import com.awesome.testing.internal.security.ServiceTokenGuard.Decision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenGuardTest {

    private final ServiceTokenGuard guard = new ServiceTokenGuard("s3cret-token");

    @Test
    void acceptsOnlyTheExactToken() {
        assertThat(guard.isEnabled()).isTrue();
        assertThat(guard.check("s3cret-token")).isEqualTo(Decision.ACCEPTED);
        assertThat(guard.check("s3cret-token ")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check("s3cret-toke")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check("S3CRET-TOKEN")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check("")).isEqualTo(Decision.REJECTED);
        assertThat(guard.check(null)).isEqualTo(Decision.REJECTED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void aBlankConfiguredTokenDisablesTheApiEvenForMatchingInput(String configured) {
        ServiceTokenGuard disabled = new ServiceTokenGuard(configured);

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.check(configured)).isEqualTo(Decision.DISABLED);
        assertThat(disabled.check("anything")).isEqualTo(Decision.DISABLED);
    }
}
