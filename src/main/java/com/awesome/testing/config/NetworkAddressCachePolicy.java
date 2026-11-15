package com.awesome.testing.config;

import java.security.Security;

/**
 * Turns off the JDK's cache of failed host-name lookups (default 10 s).
 *
 * <p>The backend starts before warehouse-service (which needs the backend's JWKS) and sends
 * initial stock to it at once, so the first lookups of {@code warehouse-service} fail while that
 * container does not exist yet. With the default cache, every retry renewed a 10-second negative
 * entry and checkout answered 503 for several seconds after warehouse-service was ready. Docker's
 * embedded DNS answers locally, so not caching failures costs nothing.
 *
 * <p>This call covers the JVM image and tests; it must run before the first lookup, because
 * {@code InetAddress} reads the policy once. The native image fixes security properties at build
 * time and ignores runtime changes, so its builder reads
 * {@code config/native/java.security.override} (pom.xml, native profile) with the same value.
 */
public final class NetworkAddressCachePolicy {

    public static final String NEGATIVE_TTL = "networkaddress.cache.negative.ttl";

    private NetworkAddressCachePolicy() {
    }

    public static void disableNegativeCaching() {
        Security.setProperty(NEGATIVE_TTL, "0");
    }
}
