package com.awesome.testing.service.webhook;

/**
 * Result of one delivery attempt. Non-retryable failures (a target the SSRF guard blocks,
 * a missing inbox) end the delivery at once; others are retried with backoff.
 */
record DeliveryOutcome(boolean success, Integer statusCode, String error, String responseSnippet, long durationMs,
                       boolean retryable) {

    static final int MAX_SNIPPET = 500;

    static DeliveryOutcome delivered(int statusCode, String body, long durationMs) {
        return new DeliveryOutcome(true, statusCode, null, snippet(body), durationMs, false);
    }

    static DeliveryOutcome failed(Integer statusCode, String error, String body, long durationMs) {
        return new DeliveryOutcome(false, statusCode, error, snippet(body), durationMs, true);
    }

    static DeliveryOutcome rejected(String error, long durationMs) {
        return new DeliveryOutcome(false, null, error, null, durationMs, false);
    }

    static String snippet(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        return body.length() <= MAX_SNIPPET ? body : body.substring(0, MAX_SNIPPET);
    }
}
