package com.awesome.testing.traffic;

import jakarta.servlet.ServletRequest;

/**
 * Carries the authenticated username of a request to the traffic recorder, which runs outside the
 * Spring Security filter chain and therefore no longer sees the security context.
 */
public final class TrafficOwner {

    static final String ATTRIBUTE = TrafficOwner.class.getName();

    private TrafficOwner() {
    }

    /** Called once the request is authenticated (bearer token) or its credentials were verified (sign-in). */
    public static void assign(ServletRequest request, String username) {
        if (username != null && !username.isBlank()) {
            request.setAttribute(ATTRIBUTE, username);
        }
    }

    public static String from(ServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof String username ? username : null;
    }
}
