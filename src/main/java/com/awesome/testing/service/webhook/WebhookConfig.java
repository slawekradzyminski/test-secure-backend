package com.awesome.testing.service.webhook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Webhook beans. The URL policy and resolver are beans so tests can replace them. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookConfig {

    @Bean
    WebhookHostResolver webhookHostResolver() {
        return WebhookHostResolver.system();
    }

    @Bean
    WebhookUrlPolicy webhookUrlPolicy(WebhookProperties properties, WebhookHostResolver webhookHostResolver) {
        return new WebhookUrlPolicy(properties, webhookHostResolver);
    }

    @Bean
    WebhookHttpSender webhookHttpSender() {
        return new WebhookHttpSender();
    }
}
