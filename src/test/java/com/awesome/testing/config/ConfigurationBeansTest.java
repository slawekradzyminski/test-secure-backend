package com.awesome.testing.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.HttpLogFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class ConfigurationBeansTest {

    @Autowired
    private OpenAPI openAPI;

    @Autowired
    private WebClient ollamaWebClient;

    @Autowired
    private HttpLogFormatter httpLogFormatter;

    @Test
    void swaggerConfigShouldCreateOpenAPIBean() {
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("JWT Authentication API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }

    @Test
    void ollamaConfigShouldCreateWebClientBean() {
        assertThat(ollamaWebClient).isNotNull();
    }

    @Test
    void logbookConfigShouldCreateHttpLogFormatterBean() {
        assertThat(httpLogFormatter).isNotNull();
        assertThat(httpLogFormatter).isInstanceOf(PrettyPrintingHttpLogFormatter.class);
    }
}

