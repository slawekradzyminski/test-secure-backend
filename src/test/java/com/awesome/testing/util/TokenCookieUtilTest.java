package com.awesome.testing.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenCookieUtilTest {

    @Test
    public void whenBuildTokenCookie_thenAllParametersShouldBeSetCorrectly() {
        // given
        String token = "testToken";
        long tokenValidityInSeconds = 1800; // 30 minutes

        // when
        String cookie = TokenCookieUtil.buildTokenCookie(token, tokenValidityInSeconds);

        // then
        assertThat(cookie).contains("token=" + token);
        assertThat(cookie).contains("Max-Age=" + tokenValidityInSeconds);
        assertThat(cookie).contains("Path=/");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("SameSite=None");
        assertThat(cookie).contains("Secure");
    }
}