package com.awesome.testing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationOpenApiCustomizerTest {

    private final CorrelationOpenApiCustomizer customizer = new CorrelationOpenApiCustomizer();

    @Test
    void referencesTheRequestIdHeaderFromEveryInlineResponse() {
        // given
        ApiResponse ok = new ApiResponse().description("ok");
        ApiResponse notFound = new ApiResponse().description("missing");
        ApiResponse shared = new ApiResponse().$ref("#/components/responses/Shared");
        Operation get = new Operation().responses(new ApiResponses().addApiResponse("200", ok).addApiResponse("404", notFound));
        Operation post = new Operation().responses(new ApiResponses().addApiResponse("500", shared));
        Operation noResponses = new Operation();
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/a", new PathItem().get(get).post(post))
                .addPathItem("/b", new PathItem().delete(noResponses)));

        // when
        customizer.customise(openApi);

        // then
        var header = openApi.getComponents().getHeaders().get("X-Request-Id");
        assertThat(header.getSchema().getMaxLength()).isEqualTo(128);
        assertThat(header.getSchema().getPattern()).isEqualTo("^[A-Za-z0-9._-]{1,128}$");
        assertThat(header.getSchema().getExample()).isNotNull();
        assertThat(header.getDescription()).contains("traceparent");
        assertThat(ok.getHeaders().get("X-Request-Id").get$ref()).isEqualTo("#/components/headers/X-Request-Id");
        assertThat(notFound.getHeaders().get("X-Request-Id").get$ref()).isEqualTo("#/components/headers/X-Request-Id");
        assertThat(shared.getHeaders()).isNull();
    }

    @Test
    void addsTheComponentToADocumentWithoutPathsOrComponents() {
        // given
        OpenAPI openApi = new OpenAPI();

        // when
        customizer.customise(openApi);

        // then
        assertThat(openApi.getComponents().getHeaders()).containsKey("X-Request-Id");
        assertThat(openApi.getPaths()).isNull();
    }
}
