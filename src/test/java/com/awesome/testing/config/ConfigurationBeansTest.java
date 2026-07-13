package com.awesome.testing.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.HttpLogFormatter;
import org.zalando.logbook.autoconfigure.LogbookProperties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestConfig.class)
class ConfigurationBeansTest {

    @Autowired
    private OpenAPI openAPI;

    @Autowired
    private WebClient ollamaWebClient;

    @Autowired
    private HttpLogFormatter httpLogFormatter;

    @Autowired
    @Qualifier("jsonBodyFieldsFilter")
    private BodyFilter logbookBodyFilter;

    @Autowired
    private LogbookProperties logbookProperties;

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

    @Test
    void logbookShouldNotExposeAuthenticationSecrets() {
        String body = "{\"password\":\"secret-password\",\"token\":\"secret-token\",\"code\":\"123456\"}";

        String filtered = logbookBodyFilter.filter("application/json", body);

        assertThat(filtered)
                .doesNotContain("secret-password", "secret-token", "123456")
                .contains("XXX");
        assertThat(logbookProperties.getExclude())
                .contains("/api/v1/users/2fa/**", "/api/v1/users/signin/2fa");
    }
}
