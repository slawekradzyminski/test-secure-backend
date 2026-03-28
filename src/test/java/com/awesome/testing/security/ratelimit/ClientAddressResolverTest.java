package com.awesome.testing.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientAddressResolverTest {

    @Test
    void shouldUseRemoteAddressWhenForwardedHeadersAreDisabled() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustForwardedHeaders(false);
        ClientAddressResolver resolver = new ClientAddressResolver(properties);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldUseFirstForwardedAddressWhenTrusted() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustForwardedHeaders(true);
        ClientAddressResolver resolver = new ClientAddressResolver(properties);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 127.0.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }
}
