package com.awesome.testing.service.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Webhook signatures: {@code X-Webhook-Signature: t=<epochSeconds>,v1=<hex>} where the hex
 * value is HMAC-SHA256 keyed with the whole secret string (UTF-8, including the
 * {@code whsec_} prefix) over {@code t + "." + body}.
 */
public final class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSigner() {
    }

    public static String signatureHeader(String secret, long epochSeconds, String body) {
        return "t=" + epochSeconds + ",v1=" + signature(secret, epochSeconds, body);
    }

    public static String signature(String secret, long epochSeconds, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal((epochSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
