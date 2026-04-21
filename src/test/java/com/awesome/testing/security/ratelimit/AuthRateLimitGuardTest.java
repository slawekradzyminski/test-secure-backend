package com.awesome.testing.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AuthRateLimitGuardTest {

    private ClientAddressResolver clientAddressResolver;
    private RateLimitService rateLimitService;
    private RateLimitProperties properties;
    private AuthRateLimitGuard guard;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        clientAddressResolver = mock(ClientAddressResolver.class);
        rateLimitService = mock(RateLimitService.class);
        properties = new RateLimitProperties();
        guard = new AuthRateLimitGuard(clientAddressResolver, properties, rateLimitService);
        request = mock(HttpServletRequest.class);
    }

    @Test
    void shouldCheckSigninIpUsernameAndCombinedDimensions() {
        when(clientAddressResolver.resolve(request)).thenReturn("203.0.113.10");

        guard.checkSignIn(request, " ClientUser ");

        verify(rateLimitService).check(
                "/api/v1/users/signin",
                "ip",
                "203.0.113.10",
                properties.getPolicies().getSigninIp()
        );
        verify(rateLimitService).check(
                "/api/v1/users/signin",
                "username",
                "clientuser",
                properties.getPolicies().getSigninUsername()
        );
        verify(rateLimitService).check(
                "/api/v1/users/signin",
                "ip_username",
                "203.0.113.10|clientuser",
                properties.getPolicies().getSigninIpUsername()
        );
    }

    @Test
    void shouldCheckOnlySigninIpWhenUsernameIsBlank() {
        when(clientAddressResolver.resolve(request)).thenReturn("203.0.113.10");

        guard.checkSignIn(request, "   ");

        verify(rateLimitService).check(
                "/api/v1/users/signin",
                "ip",
                "203.0.113.10",
                properties.getPolicies().getSigninIp()
        );
        verifyNoMoreInteractions(rateLimitService);
    }

    @Test
    void shouldRateLimitOllamaByUsernameWhenAuthenticated() {
        guard.checkOllama(request, " ClientUser ");

        verify(rateLimitService).check(
                "/api/v1/ollama",
                "username",
                "clientuser",
                properties.getPolicies().getOllamaUser()
        );
        verify(clientAddressResolver, never()).resolve(request);
    }

    @Test
    void shouldRateLimitOllamaByIpWhenPrincipalIsMissing() {
        when(clientAddressResolver.resolve(request)).thenReturn("203.0.113.10");

        guard.checkOllama(request, null);

        verify(rateLimitService).check(
                "/api/v1/ollama",
                "ip",
                "203.0.113.10",
                properties.getPolicies().getOllamaIp()
        );
    }

    @Test
    void shouldRateLimitEmailAndQrByUsernameWhenAuthenticated() {
        guard.checkEmail(request, " ClientUser ");
        guard.checkQr(request, " ClientUser ");

        verify(rateLimitService).check(
                "/api/v1/email",
                "username",
                "clientuser",
                properties.getPolicies().getEmailUser()
        );
        verify(rateLimitService).check(
                "/api/v1/qr/create",
                "username",
                "clientuser",
                properties.getPolicies().getQrUser()
        );
    }
}
