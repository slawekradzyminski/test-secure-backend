package com.awesome.testing.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
    @NotBlank
    private String baseUrl = "http://localhost:11434";
} 