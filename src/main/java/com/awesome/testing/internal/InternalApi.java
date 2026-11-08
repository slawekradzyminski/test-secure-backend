package com.awesome.testing.internal;

import lombok.experimental.UtilityClass;

/**
 * Service-to-service API called by warehouse-service. It is not routed by the
 * gateway, is authenticated with {@code X-Service-Token} instead of a JWT, and
 * is kept out of CORS, traffic capture, request logging and the public
 * OpenAPI document.
 */
@UtilityClass
public class InternalApi {

    public static final String PREFIX = "/internal";
    public static final String BASE_PATH = PREFIX + "/v1";
    public static final String TOKEN_HEADER = "X-Service-Token";

    /** True for {@code /internal} and everything below it, but not for {@code /internalx}. */
    public static boolean isInternalPath(String path) {
        return path != null && (path.equals(PREFIX) || path.startsWith(PREFIX + "/"));
    }
}
