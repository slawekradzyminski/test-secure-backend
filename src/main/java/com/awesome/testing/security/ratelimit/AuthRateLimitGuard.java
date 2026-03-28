package com.awesome.testing.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AuthRateLimitGuard {

    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";
    private static final String SIGNIN_ENDPOINT = "/api/v1/users/signin";
    private static final String PASSWORD_FORGOT_ENDPOINT = "/api/v1/users/password/forgot";
    private static final String PASSWORD_RESET_ENDPOINT = "/api/v1/users/password/reset";
    private static final String REFRESH_ENDPOINT = "/api/v1/users/refresh";
    private static final String EMAIL_ENDPOINT = "/api/v1/email";
    private static final String QR_ENDPOINT = "/api/v1/qr/create";
    private static final String OLLAMA_ENDPOINT = "/api/v1/ollama";

    private final ClientAddressResolver clientAddressResolver;
    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;

    public void checkSignUp(HttpServletRequest request) {
        String clientIp = clientAddressResolver.resolve(request);
        rateLimitService.check(SIGNUP_ENDPOINT, "ip", clientIp, properties.getPolicies().getSignupIp());
    }

    public void checkSignIn(HttpServletRequest request, String username) {
        String clientIp = clientAddressResolver.resolve(request);
        rateLimitService.check(SIGNIN_ENDPOINT, "ip", clientIp, properties.getPolicies().getSigninIp());

        String normalizedUsername = normalize(username);
        if (normalizedUsername != null) {
            rateLimitService.check(SIGNIN_ENDPOINT, "username", normalizedUsername, properties.getPolicies().getSigninUsername());
            rateLimitService.check(
                    SIGNIN_ENDPOINT,
                    "ip_username",
                    clientIp + "|" + normalizedUsername,
                    properties.getPolicies().getSigninIpUsername());
        }
    }

    public void checkForgotPassword(HttpServletRequest request, String identifier) {
        String clientIp = clientAddressResolver.resolve(request);
        rateLimitService.check(PASSWORD_FORGOT_ENDPOINT, "ip", clientIp, properties.getPolicies().getPasswordForgotIp());

        String normalizedIdentifier = normalize(identifier);
        if (normalizedIdentifier != null) {
            rateLimitService.check(PASSWORD_FORGOT_ENDPOINT, "identifier", normalizedIdentifier,
                    properties.getPolicies().getPasswordForgotIdentifier());
        }
    }

    public void checkResetPassword(HttpServletRequest request) {
        String clientIp = clientAddressResolver.resolve(request);
        rateLimitService.check(PASSWORD_RESET_ENDPOINT, "ip", clientIp, properties.getPolicies().getPasswordResetIp());
    }

    public void checkRefresh(HttpServletRequest request) {
        String clientIp = clientAddressResolver.resolve(request);
        rateLimitService.check(REFRESH_ENDPOINT, "ip", clientIp, properties.getPolicies().getRefreshIp());
    }

    public void checkEmail(HttpServletRequest request, String username) {
        checkAuthenticatedOrIp(request, username, EMAIL_ENDPOINT, properties.getPolicies().getEmailUser());
    }

    public void checkQr(HttpServletRequest request, String username) {
        checkAuthenticatedOrIp(request, username, QR_ENDPOINT, properties.getPolicies().getQrUser());
    }

    public void checkOllama(HttpServletRequest request, String username) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername != null) {
            rateLimitService.check(OLLAMA_ENDPOINT, "username", normalizedUsername, properties.getPolicies().getOllamaUser());
            return;
        }

        String clientIp = clientAddressResolver.resolve(request);
        rateLimitService.check(OLLAMA_ENDPOINT, "ip", clientIp, properties.getPolicies().getOllamaIp());
    }

    private void checkAuthenticatedOrIp(HttpServletRequest request,
                                        String username,
                                        String endpoint,
                                        RateLimitProperties.Policy authenticatedPolicy) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername != null) {
            rateLimitService.check(endpoint, "username", normalizedUsername, authenticatedPolicy);
            return;
        }

        String clientIp = clientAddressResolver.resolve(request);
        rateLimitService.check(endpoint, "ip", clientIp, authenticatedPolicy);
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
