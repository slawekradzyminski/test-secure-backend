package com.awesome.testing.config;

import com.awesome.testing.internal.security.PaymentSignatureVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * payment-service integration: {@code payment.webhook-secret} ({@code PAYMENT_WEBHOOK_SECRET})
 * signs the payment results it posts here. The backend never calls payment-service; card
 * refunds reach it through the internal event feed ({@code card-refund.requested}).
 */
@Configuration(proxyBeanMethods = false)
public class PaymentConfig {

    @Bean
    PaymentSignatureVerifier paymentSignatureVerifier(
            @Value("${payment.webhook-secret:}") String webhookSecret, Clock commerceClock) {
        return new PaymentSignatureVerifier(webhookSecret, commerceClock);
    }
}
