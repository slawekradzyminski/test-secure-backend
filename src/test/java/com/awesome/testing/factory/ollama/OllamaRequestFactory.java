package com.awesome.testing.factory.ollama;

import com.awesome.testing.dto.ollama.StreamedRequestDto;
import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class OllamaRequestFactory {

    public static StreamedRequestDto validStreamedRequest() {
        return StreamedRequestDto.builder()
                .model("llama3.2:1b")
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

    public static ChatRequestDto validChatRequest() {
        return ChatRequestDto.builder()
                .model("llama3.2:1b")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Hello")
                                .build()
                ))
                .stream(true)
                .build();
    }

    public static ChatRequestDto invalidChatRequest() {
        return ChatRequestDto.builder()
                .model("")
                .messages(List.of())
                .build();
    }

}
