package com.awesome.testing.config;

import com.awesome.testing.security.ratelimit.RateLimitProperties;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class RateLimitOpenApiCustomizer implements OperationCustomizer {

    private static final String TOO_MANY_REQUESTS = "429";

    private final RateLimitProperties rateLimitProperties;

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (rateLimitProperties.isEnabled()) {
            return operation;
        }

        Map<String, ApiResponse> responses = operation.getResponses();
        if (responses != null) {
            responses.remove(TOO_MANY_REQUESTS);
        }
        return operation;
    }
}
