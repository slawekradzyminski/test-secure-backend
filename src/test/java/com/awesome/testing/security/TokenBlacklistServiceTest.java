package com.awesome.testing.security;

import com.awesome.testing.AbstractUnitTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenBlacklistServiceTest extends AbstractUnitTest {

    private final TokenBlacklistService tokenBlacklistService = new TokenBlacklistService();

    @Test
    public void whenTokenIsAddedToBlacklist_thenItShouldBeBlacklisted() {
        // given
        String token = "blacklistedToken";

        // when
        tokenBlacklistService.addToBlacklist(token);

        // then
        assertThat(tokenBlacklistService.isBlacklisted(token)).isTrue();
    }

    @Test
    public void whenTokenIsNotAddedToBlacklist_thenItShouldNotBeBlacklisted() {
        // given
        String token = "notBlacklistedToken";

        // when
        // No action taken to add to blacklist

        // then
        assertThat(tokenBlacklistService.isBlacklisted(token)).isFalse();
    }
}