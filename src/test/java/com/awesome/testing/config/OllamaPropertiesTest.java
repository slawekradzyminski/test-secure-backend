package com.awesome.testing.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestConfig.class)
class OllamaPropertiesTest {

    @Autowired
    private OllamaProperties ollamaProperties;

    @Test
    void shouldLoadOllamaProperties() {
        assertThat(ollamaProperties).isNotNull();
        assertThat(ollamaProperties.getBaseUrl()).isNotNull();
    }

    @Test
    void shouldAllowSettingBaseUrl() {
        OllamaProperties properties = new OllamaProperties();
        properties.setBaseUrl("http://localhost:11434");
        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:11434");
    }
}

