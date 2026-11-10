package com.awesome.testing.internal.security;

/**
 * A service allowed to call the internal API with its own {@code X-Service-Token},
 * configured as {@code internal.service-tokens.warehouse} and
 * {@code internal.service-tokens.payment}.
 */
public enum ServiceCaller {
    WAREHOUSE,
    PAYMENT
}
