package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.internal.security.PaymentSignatureVerifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

/** Signs and posts payment results the way payment-service does. */
abstract class AbstractPaymentFlowTest extends AbstractCommerceFlowTest {

    protected static final String PAYMENT_TOKEN = "test-payment-token";
    protected static final String WAREHOUSE_TOKEN = "test-warehouse-token";
    protected static final String WEBHOOK_SECRET = "test-payment-webhook-secret";

    protected static String body(UUID requestId, UUID paymentId, String status, String amount, String failureCode) {
        return """
                {"requestId":"%s","paymentId":"%s","status":"%s","amount":%s,"currency":"USD","failureCode":%s}"""
                .formatted(requestId, paymentId, status, amount, failureCode == null ? "null" : "\"" + failureCode + "\"");
    }

    protected static String captured(UUID requestId, UUID paymentId, String amount) {
        return body(requestId, paymentId, "CAPTURED", amount, null);
    }

    protected static String failed(UUID requestId, UUID paymentId, String amount, String failureCode) {
        return body(requestId, paymentId, "FAILED", amount, failureCode);
    }

    protected <T> ResponseEntity<T> postResult(Long orderId, String rawBody, Class<T> type) {
        return postResult(orderId, rawBody, signedHeaders(rawBody, Instant.now().getEpochSecond()), type);
    }

    protected <T> ResponseEntity<T> postResult(Long orderId, String rawBody, HttpHeaders headers, Class<T> type) {
        return executePost("/internal/v1/orders/" + orderId + "/payment", rawBody, headers, type);
    }

    protected HttpHeaders signedHeaders(String rawBody, long epochSeconds) {
        HttpHeaders headers = serviceHeaders(PAYMENT_TOKEN);
        headers.add(PaymentSignatureVerifier.HEADER, PaymentSignatureVerifier.sign(WEBHOOK_SECRET, epochSeconds, rawBody));
        return headers;
    }

    protected HttpHeaders serviceHeaders(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", token);
        return headers;
    }
}
