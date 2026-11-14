package com.awesome.testing.internal.security;

/**
 * A service allowed to call the internal API with its own {@code X-Service-Token},
 * configured as {@code internal.service-tokens.warehouse},
 * {@code internal.service-tokens.payment} and {@code internal.service-tokens.notification}.
 */
public enum ServiceCaller {
    WAREHOUSE,
    PAYMENT,
    NOTIFICATION
}
