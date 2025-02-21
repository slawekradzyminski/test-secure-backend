package com.awesome.testing.factory.ollama;

import com.awesome.testing.dto.ollama.GenerateRequestDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OllamaRequestFactory {

    public static GenerateRequestDto validGenerateRequest() {
        return GenerateRequestDto.builder()
                .model("gemma:2b")
                .prompt("test prompt")
                .stream(true)
                .options(null)
                .build();
    }

    public static GenerateRequestDto invalidGenerateRequest() {
        return GenerateRequestDto.builder()
                .model("")
                .prompt("")
                .stream(true)
                .options(null)
                .build();
    }

}
