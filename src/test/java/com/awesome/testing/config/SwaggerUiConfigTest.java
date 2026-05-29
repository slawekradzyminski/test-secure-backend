package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
class SwaggerUiConfigTest extends HttpHelper {

    @Test
    void swaggerUiConfigShouldLinkJsonAndYamlApiDocs() throws Exception {
        ResponseEntity<String> response = executeGet(
                "/v3/api-docs/swagger-config",
                getJsonOnlyHeaders(),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode config = new ObjectMapper().readTree(response.getBody());

        assertThat(config.path("urls.primaryName").asText()).isEqualTo("OpenAPI JSON");
        assertThat(configuredUrls(config))
                .containsExactlyInAnyOrder(
                        "/v3/api-docs",
                        "/v3/api-docs.yaml"
                );
    }

    private List<String> configuredUrls(JsonNode config) {
        JsonNode urls = config.path("urls");
        assertThat(urls.isArray()).isTrue();

        List<String> configuredUrls = new ArrayList<>();
        for (JsonNode url : urls) {
            configuredUrls.add(url.path("url").asText());
        }
        return configuredUrls;
    }
}
