package com.awesome.testing.traffic;

import java.util.regex.Pattern;

public final class TrafficSession {

    public static final String HEADER = "X-Client-Session-Id";
    public static final String STOMP_ATTRIBUTE = "trafficClientSessionId";
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{16,128}");

    private TrafficSession() {
    }

    public static String requireValid(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!VALID_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("A valid X-Client-Session-Id header is required");
        }
        return normalized;
    }

    public static String validOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return VALID_ID.matcher(normalized).matches() ? normalized : null;
    }

    public static String topic(String clientSessionId) {
        return "/topic/traffic/" + requireValid(clientSessionId);
    }
}
