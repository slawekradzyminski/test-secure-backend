package com.awesome.testing.internal.controller;

import com.awesome.testing.internal.InternalApi;
import com.awesome.testing.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public key set for access-token verification. warehouse-service verifies tokens with
 * it instead of sharing the signing secret; undocumented in OpenAPI on purpose.
 */
@RestController
@RequiredArgsConstructor
public class InternalJwksController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping(InternalApi.BASE_PATH + "/jwks.json")
    public Map<String, Object> jwks() {
        return jwtTokenProvider.jwks();
    }
}
