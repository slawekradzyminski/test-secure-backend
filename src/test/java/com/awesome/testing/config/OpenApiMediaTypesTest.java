package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** T-028: documented success bodies carry their real media type and schema. */
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false"
})
class OpenApiMediaTypesTest extends HttpHelper {

    @Test
    void noResponseIsDocumentedAsAnyMediaType() throws Exception {
        // given
        OpenApiDocument document = new OpenApiDocument(spec());

        // when
        List<String> wildcard = document.operations()
                .flatMap(OpenApiDocument.DocumentedOperation::responses)
                .filter(response -> response.node().path("content").has("*/*"))
                .map(response -> response.endpoint() + " " + response.status())
                .toList();

        // then
        assertThat(wildcard).isEmpty();
    }

    @Test
    void userEditReturnsTheUserResponseDto() throws Exception {
        // given
        OpenApiDocument document = new OpenApiDocument(spec());

        // when
        JsonNode ok = document.response(new OpenApiDocument.Endpoint("/api/v1/users/{username}", "put"), "200");

        // then
        assertThat(ok.path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/UserResponseDto");
        assertThat(spec().path("components").path("schemas").has("UserEntity")).isFalse();
    }

    private JsonNode spec() throws Exception {
        return new ObjectMapper().readTree(executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class).getBody());
    }
}
