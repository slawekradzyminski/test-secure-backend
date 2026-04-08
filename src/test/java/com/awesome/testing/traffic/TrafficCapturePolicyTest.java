package com.awesome.testing.traffic;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrafficCapturePolicyTest {

    private TrafficCapturePolicy trafficCapturePolicy;
    private TrafficProperties trafficProperties;

    @BeforeEach
    void setUp() {
        trafficProperties = new TrafficProperties();
        trafficCapturePolicy = new TrafficCapturePolicy(trafficProperties);
    }

    @Test
    void shouldCaptureDocumentedRestControllerEndpoint() {
        HttpServletRequest request = mockRequest(
                "/api/test",
                handlerMethod(DocumentedController.class, "documentedEndpoint")
        );

        assertThat(trafficCapturePolicy.shouldCapture(request)).isTrue();
    }

    @Test
    void shouldSkipExcludedPathEvenWhenDocumented() {
        HttpServletRequest request = mockRequest(
                "/api/v1/traffic/logs",
                handlerMethod(DocumentedController.class, "documentedEndpoint")
        );

        assertThat(trafficCapturePolicy.shouldCapture(request)).isFalse();
    }

    @Test
    void shouldSkipRequestWithoutHandlerMethod() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/login");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)).thenReturn(null);

        assertThat(trafficCapturePolicy.shouldCapture(request)).isFalse();
    }

    @Test
    void shouldSkipUndocumentedControllerEndpoint() {
        HttpServletRequest request = mockRequest(
                "/actuator/health",
                handlerMethod(UndocumentedController.class, "undocumentedEndpoint")
        );

        assertThat(trafficCapturePolicy.shouldCapture(request)).isFalse();
    }

    @Test
    void shouldCaptureTaggedControllerEndpoint() {
        HttpServletRequest request = mockRequest(
                "/api/tagged",
                handlerMethod(TaggedController.class, "taggedEndpoint")
        );

        assertThat(trafficCapturePolicy.shouldCapture(request)).isTrue();
    }

    private HttpServletRequest mockRequest(String path, Object handler) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)).thenReturn(handler);
        return request;
    }

    private HandlerMethod handlerMethod(Class<?> controllerType, String methodName) {
        try {
            return new HandlerMethod(controllerType.getDeclaredConstructor().newInstance(),
                    controllerType.getDeclaredMethod(methodName));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @RestController
    @RequestMapping("/api/test")
    static class DocumentedController {

        @Operation(summary = "Documented")
        @GetMapping
        void documentedEndpoint() {
        }
    }

    @RestController
    @RequestMapping("/api/tagged")
    @Tag(name = "tagged")
    static class TaggedController {

        @GetMapping
        void taggedEndpoint() {
        }
    }

    @RestController
    @RequestMapping("/actuator")
    static class UndocumentedController {

        @GetMapping("/health")
        void undocumentedEndpoint() {
        }
    }
}
