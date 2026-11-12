package com.awesome.testing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    static final String DESCRIPTION = """
            This is a sample JWT authentication service.

            Error bodies come in three documented shapes:
            - `ErrorDto` `{"message": "..."}` for business, authentication, authorization, unknown-path (404) and
              unsupported-method (405) errors;
            - `ValidationErrorsDto` `{"<field>": "<message>"}` for invalid request bodies, one message per field;
            - `{"error": "..."}` (also a `ValidationErrorsDto`) when a body cannot be read at all or a path or query
              parameter does not convert.
            Two endpoints answer 404 with an empty body: deleting an unknown product and reading the traffic of an
            unknown correlation id.""";

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .info(new Info()
                        .title("JWT Authentication API")
                        .version("1.0")
                        .description(DESCRIPTION)
                        .termsOfService("http://swagger.io/terms/")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")));
    }
}
