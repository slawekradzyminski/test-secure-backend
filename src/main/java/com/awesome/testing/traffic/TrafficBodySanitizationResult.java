package com.awesome.testing.traffic;

public record TrafficBodySanitizationResult(
        String body,
        boolean truncated,
        int originalLength,
        int storedLength
) {

    public static TrafficBodySanitizationResult empty() {
        return new TrafficBodySanitizationResult("", false, 0, 0);
    }
}
