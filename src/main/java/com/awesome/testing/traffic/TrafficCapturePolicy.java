package com.awesome.testing.traffic;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@Component
@RequiredArgsConstructor
public class TrafficCapturePolicy {

    private final TrafficProperties trafficProperties;

    public boolean shouldCapture(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (isExcludedPath(path)) {
            return false;
        }

        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }

        Class<?> beanType = handlerMethod.getBeanType();
        return AnnotatedElementUtils.hasAnnotation(beanType, RestController.class)
                && isDocumentedInSwagger(handlerMethod);
    }

    private boolean isExcludedPath(String path) {
        return trafficProperties.getExcludedPaths().stream().anyMatch(excluded ->
                path.equals(excluded) || path.startsWith(excluded.endsWith("/") ? excluded : excluded + "/"));
    }

    private boolean isDocumentedInSwagger(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), Operation.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), Tag.class);
    }
}
