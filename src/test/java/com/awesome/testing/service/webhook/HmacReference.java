package com.awesome.testing.service.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Independent HMAC-SHA256 computation used to check webhook signatures in tests. */
public final class HmacReference {

    private HmacReference() {
    }

    public static String hex(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** True when {@code header} ({@code t=...,v1=...}) signs {@code body} with {@code secret}. */
    public static boolean verifies(String header, String secret, String body) {
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            if (part.startsWith("t=")) {
                timestamp = part.substring(2);
            } else if (part.startsWith("v1=")) {
                signature = part.substring(3);
            }
        }
        return timestamp != null && signature != null && hex(secret, timestamp + "." + body).equals(signature);
    }
}
