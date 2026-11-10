package com.awesome.testing.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies {@code X-Payment-Signature: t=<epochSeconds>,v1=<hex>} on payment result
 * notifications, where {@code hex = HMAC-SHA256(PAYMENT_WEBHOOK_SECRET, t + "." + rawBody)}.
 * Timestamps more than {@link #TOLERANCE} away from now are rejected so a captured request
 * cannot be replayed later; several {@code v1} values are allowed (secret rotation) and any
 * match is enough. Digests are compared in constant time. A blank secret rejects everything.
 */
public final class PaymentSignatureVerifier {

    public static final String HEADER = "X-Payment-Signature";
    static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Clock clock;

    public PaymentSignatureVerifier(String webhookSecret, Clock clock) {
        this.secret = webhookSecret == null || webhookSecret.isBlank()
                ? new byte[0]
                : webhookSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public boolean isConfigured() {
        return secret.length > 0;
    }

    /** True when {@code header} carries a fresh timestamp and a v1 signature of {@code rawBody}. */
    public boolean verify(String header, String rawBody) {
        if (!isConfigured() || header == null || rawBody == null) {
            return false;
        }
        Long timestamp = null;
        List<byte[]> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (key.equals("t")) {
                if (timestamp != null) {
                    return false;
                }
                timestamp = parseLong(value);
                if (timestamp == null) {
                    return false;
                }
            } else if (key.equals("v1")) {
                byte[] presented = parseHex(value);
                if (presented.length > 0) {
                    signatures.add(presented);
                }
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }
        byte[] expected = hmac(secret, timestamp + "." + rawBody);
        boolean matched = false;
        for (byte[] presented : signatures) {
            // Non-short-circuit: every candidate is compared.
            matched |= MessageDigest.isEqual(expected, presented);
        }
        return matched && isFresh(timestamp);
    }

    /** The header payment-service would send for {@code rawBody} at {@code epochSeconds}. */
    public static String sign(String webhookSecret, long epochSeconds, String rawBody) {
        return "t=" + epochSeconds + ",v1=" + HexFormat.of().formatHex(
                hmac(webhookSecret.getBytes(StandardCharsets.UTF_8), epochSeconds + "." + rawBody));
    }

    private boolean isFresh(long epochSeconds) {
        long skew = Math.abs(clock.instant().getEpochSecond() - epochSeconds);
        return skew <= TOLERANCE.toSeconds();
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] parseHex(String value) {
        try {
            return HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
