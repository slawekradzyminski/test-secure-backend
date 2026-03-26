package com.awesome.testing.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class OpenApiEndpointDocumentationCoverageTest {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void everyControllerEndpointShouldHaveOpenApiSummaryAndResponses() {
        List<String> violations = new ArrayList<>();

        requestMappingHandlerMapping.getHandlerMethods()
                .forEach((mapping, handlerMethod) -> validateHandler(mapping, handlerMethod, violations));

        assertThat(violations)
                .withFailMessage("Missing OpenAPI annotations:\n%s", String.join("\n", violations))
                .isEmpty();
    }

    private void validateHandler(RequestMappingInfo mapping, HandlerMethod handlerMethod, List<String> violations) {
        Class<?> beanType = handlerMethod.getBeanType();
        if (!beanType.getPackageName().startsWith("com.awesome.testing.controller")) {
            return;
        }

        String endpoint = formatEndpoint(mapping, handlerMethod);

        Operation operation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), Operation.class);
        if (operation == null || operation.summary().isBlank()) {
            violations.add(endpoint + " is missing @Operation with a non-empty summary");
        }

        ApiResponses apiResponses = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiResponses.class);
        ApiResponse[] directResponses = AnnotatedElementUtils.findMergedRepeatableAnnotations(
                handlerMethod.getMethod(),
                ApiResponse.class
        ).toArray(ApiResponse[]::new);

        boolean hasResponses = apiResponses != null && apiResponses.value().length > 0;
        if (!hasResponses && directResponses.length == 0) {
            violations.add(endpoint + " is missing @ApiResponses/@ApiResponse");
        }
    }

    private String formatEndpoint(RequestMappingInfo mapping, HandlerMethod handlerMethod) {
        Set<String> methods = mapping.getMethodsCondition().getMethods().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
        String methodValue = methods.isEmpty() ? "ANY" : String.join(",", methods);

        Set<String> paths = mapping.getPatternValues();
        String pathValue = paths.isEmpty() ? "<unknown-path>" : String.join(",", paths);

        return methodValue + " " + pathValue + " -> "
                + handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
    }
}
