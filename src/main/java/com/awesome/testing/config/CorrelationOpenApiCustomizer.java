package com.awesome.testing.config;

import com.awesome.testing.correlation.CorrelationIds;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Documents the {@code X-Request-Id} header that {@link com.awesome.testing.correlation.CorrelationFilter}
 * returns on every response: one reusable header component referenced from every documented
 * response (responses that are references themselves are left alone).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CorrelationOpenApiCustomizer implements OpenApiCustomizer {

    static final String HEADER_REF = "#/components/headers/" + CorrelationIds.REQUEST_ID_HEADER;

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents().addHeaders(CorrelationIds.REQUEST_ID_HEADER, requestIdHeader());
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().stream()
                .flatMap(path -> path.readOperations().stream())
                .map(io.swagger.v3.oas.models.Operation::getResponses)
                .filter(Objects::nonNull)
                .flatMap(responses -> responses.values().stream())
                .filter(response -> response.get$ref() == null)
                .forEach(CorrelationOpenApiCustomizer::addRequestIdHeader);
    }

    static Header requestIdHeader() {
        StringSchema schema = new StringSchema();
        schema.setPattern("^[A-Za-z0-9._-]{1,128}$");
        schema.setMaxLength(CorrelationIds.MAX_REQUEST_ID_LENGTH);
        schema.setExample("3f2c1a9e-7d4b-4c55-9a0e-2b6f8d1c4e77");
        return new Header()
                .description("Correlation id of the request: the caller's valid X-Request-Id "
                        + "(1-128 characters of A-Z a-z 0-9 . _ -) or a generated UUID. Send it again with a "
                        + "W3C traceparent header to continue a trace; logs, domain events and webhook "
                        + "deliveries caused by the request carry the same id.")
                .schema(schema);
    }

    private static void addRequestIdHeader(ApiResponse response) {
        response.addHeaderObject(CorrelationIds.REQUEST_ID_HEADER, new Header().$ref(HEADER_REF));
    }
}
