package com.awesome.testing.internal;

import com.awesome.testing.internal.security.ServiceTokenFilter;
import com.awesome.testing.internal.security.ServiceTokenGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalApiConfig {

    @Bean
    ServiceTokenGuard serviceTokenGuard(@Value("${warehouse.service-token:}") String serviceToken) {
        return new ServiceTokenGuard(serviceToken);
    }

    /** Registered just before the Spring Security filter chain so rejected calls never reach it. */
    @Bean
    FilterRegistrationBean<ServiceTokenFilter> serviceTokenFilter(ServiceTokenGuard serviceTokenGuard) {
        FilterRegistrationBean<ServiceTokenFilter> registration =
                new FilterRegistrationBean<>(new ServiceTokenFilter(serviceTokenGuard));
        registration.addUrlPatterns(InternalApi.PREFIX, InternalApi.PREFIX + "/*");
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }
}
