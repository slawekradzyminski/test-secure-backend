package com.awesome.testing.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpenApiResponseOrderCustomizer implements OperationCustomizer {

    private static final int DEFAULT_RESPONSE_ORDER = Integer.MAX_VALUE;

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Map<String, ApiResponse> responses = operation.getResponses();
        if (responses == null || responses.size() < 2) {
            return operation;
        }

        Map<String, ApiResponse> sortedResponses = new LinkedHashMap<>();
        responses.entrySet().stream()
                .sorted(Comparator.comparingInt(OpenApiResponseOrderCustomizer::responseOrder))
                .forEach(entry -> sortedResponses.put(entry.getKey(), entry.getValue()));
        responses.clear();
        responses.putAll(sortedResponses);
        return operation;
    }

    private static int responseOrder(Map.Entry<String, ApiResponse> response) {
        try {
            return Integer.parseInt(response.getKey());
        } catch (NumberFormatException ex) {
            return DEFAULT_RESPONSE_ORDER;
        }
    }
}
