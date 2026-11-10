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
            @Value("${internal.service-tokens.payment:}") String paymentToken) {
        return new ServiceTokenGuard(Map.of(
                ServiceCaller.WAREHOUSE, warehouseToken,
                ServiceCaller.PAYMENT, paymentToken));
    }

    /**
     * Which caller each internal path accepts. Payment endpoints are added here as
     * {@code .route("/internal/v1/orders/\\d+/payment", ServiceCaller.PAYMENT)}; everything
     * else is the warehouse-service API.
     */
    @Bean
    InternalRoutes internalRoutes() {
        return InternalRoutes.builder()
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
