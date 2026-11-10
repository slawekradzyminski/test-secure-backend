package com.awesome.testing.config;

import com.awesome.testing.internal.security.PaymentSignatureVerifier;
import com.awesome.testing.service.payment.PaymentServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * payment-service integration: {@code payment.webhook-secret} ({@code PAYMENT_WEBHOOK_SECRET})
 * signs the payment results it posts here, and {@code payment.base-url}
 * ({@code PAYMENT_BASE_URL}) is where card refunds go, authenticated with the payment
 * service token.
 */
@Configuration(proxyBeanMethods = false)
public class PaymentConfig {

    @Bean
    PaymentSignatureVerifier paymentSignatureVerifier(
            @Value("${payment.webhook-secret:}") String webhookSecret, Clock commerceClock) {
        return new PaymentSignatureVerifier(webhookSecret, commerceClock);
    }

    @Bean
    PaymentServiceClient paymentServiceClient(
            @Value("${payment.base-url:http://payment-service:4004}") String baseUrl,
            @Value("${internal.service-tokens.payment:}") String serviceToken,
            @Value("${payment.timeout:PT5S}") Duration timeout) {
        return new PaymentServiceClient(baseUrl, serviceToken, timeout);
    }
}
