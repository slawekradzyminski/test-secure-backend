package com.awesome.testing.factory.ollama;

import com.awesome.testing.dto.ollama.StreamedRequestDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OllamaRequestFactory {

    public static StreamedRequestDto validStreamedRequest() {
        return StreamedRequestDto.builder()
                .model("gemma:2b")
                .prompt("test prompt")
                .options(null)
                .build();
    }

    public static StreamedRequestDto invalidStreamedRequest() {
        return StreamedRequestDto.builder()
                .model("")
                .prompt("")
                .options(null)
                .build();
    }

}
