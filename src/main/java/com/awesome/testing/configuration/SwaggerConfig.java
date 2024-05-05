package com.awesome.testing.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.servers.Server;

import java.util.Collections;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("Authorization",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("token")))
                .info(new Info().title("JSON Web Token Authentication API")
                        .description(
                                "This is a sample JWT authentication service. You can find out more about JWT at [https://jwt.io/](https://jwt.io/)." +
                                        " For this sample, you can use the `admin` or `client` users (password: admin and client respectively) to test the Authorization filters." +
                                        " Once you have successfully logged in and obtained the token, you should click on the right top button `Authorize` and copy paste token.")
                        .version("1.0.0").license(new io.swagger.v3.oas.models.info.License().name("MIT License")
                                .url("http://opensource.org/licenses/MIT"))
                        .contact(new Contact().email("slawekradz@gmail.com")))
                .addTagsItem(new Tag().name("users").description("Operations about users"))
                .addSecurityItem(new SecurityRequirement().addList("Authorization", Collections.emptyList()))
                .servers(List.of(new Server().url("https://awesome-testing-1681473100176.ew.r.appspot.com").description("Prod server")));
    }
}