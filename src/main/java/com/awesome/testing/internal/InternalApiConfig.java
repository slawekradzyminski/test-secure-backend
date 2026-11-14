package com.awesome.testing.internal;

import com.awesome.testing.internal.security.InternalRoutes;
import com.awesome.testing.internal.security.ServiceCaller;
import com.awesome.testing.internal.security.ServiceTokenFilter;
import com.awesome.testing.internal.security.ServiceTokenGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class InternalApiConfig {

    /**
     * One token per caller; a blank token disables that caller's endpoints (404).
     * {@code warehouse.service-token} remains an alias for the warehouse token.
     */
    @Bean
    ServiceTokenGuard serviceTokenGuard(
            @Value("${internal.service-tokens.warehouse:${warehouse.service-token:}}") String warehouseToken,
            @Value("${internal.service-tokens.payment:}") String paymentToken,
            @Value("${internal.service-tokens.notification:}") String notificationToken) {
        return new ServiceTokenGuard(Map.of(
                ServiceCaller.WAREHOUSE, warehouseToken,
                ServiceCaller.PAYMENT, paymentToken,
                ServiceCaller.NOTIFICATION, notificationToken));
    }

    /**
     * Which caller each internal path accepts (first full match wins). payment-service posts
     * payment results and reads single orders, the JWKS and the event feed; notification-service
     * reads the JWKS, the event feed and the one-time webhook export; everything else is the
     * warehouse-service API.
     */
    @Bean
    InternalRoutes internalRoutes() {
        return InternalRoutes.builder()
                .route("/internal/v1/orders/\\d+/payment", ServiceCaller.PAYMENT)
                .route("/internal/v1/orders/\\d+", ServiceCaller.WAREHOUSE, ServiceCaller.PAYMENT)
                .route("/internal/v1/jwks\\.json", ServiceCaller.WAREHOUSE, ServiceCaller.PAYMENT,
                        ServiceCaller.NOTIFICATION)
                .route("/internal/v1/events", ServiceCaller.WAREHOUSE, ServiceCaller.PAYMENT, ServiceCaller.NOTIFICATION)
                .route("/internal/v1/webhooks/export", ServiceCaller.NOTIFICATION)
                .otherwise(ServiceCaller.WAREHOUSE)
                .build();
    }

    /** Registered just before the Spring Security filter chain so rejected calls never reach it. */
    @Bean
    FilterRegistrationBean<ServiceTokenFilter> serviceTokenFilter(
            ServiceTokenGuard serviceTokenGuard, InternalRoutes internalRoutes) {
        FilterRegistrationBean<ServiceTokenFilter> registration =
                new FilterRegistrationBean<>(new ServiceTokenFilter(serviceTokenGuard, internalRoutes));
        registration.addUrlPatterns(InternalApi.PREFIX, InternalApi.PREFIX + "/*");
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }
}
