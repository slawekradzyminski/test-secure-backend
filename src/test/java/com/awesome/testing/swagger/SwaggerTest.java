package com.awesome.testing.swagger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Copy paste from:
 * https://github.com/springdoc/springdoc-openapi/blob/main/springdoc-openapi-starter-webmvc-ui/src/test/java/test/org/springdoc/ui/app1/SpringDocApp1Test.java
 */
public class SwaggerTest extends AbstractSpringDocTest {

    @Test
    public void shouldDisplaySwaggerUiPage() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }

    @Test
    public void originalIndex() throws Exception {
        super.checkJS();
    }

}
