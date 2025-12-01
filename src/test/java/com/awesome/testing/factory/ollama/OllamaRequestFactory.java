package com.awesome.testing.factory.ollama;

import com.awesome.testing.dto.ollama.*;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;

@UtilityClass
public class OllamaRequestFactory {

    public static StreamedRequestDto validStreamedRequest() {
        return StreamedRequestDto.builder()
                .model("qwen3:0.6b")
                .prompt("test prompt")
                .options(null)
                .build();
    }

    public static StreamedRequestDto validStreamedRequestWithThink() {
        return StreamedRequestDto.builder()
                .model("qwen3:0.6b")
                .prompt("test prompt")
                .options(null)
                .think(true)
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
                .model("qwen3:0.6b")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Hello")
                                .build()
                ))
                .stream(true)
                .build();
    }

    public static ChatRequestDto validChatRequestWithThink() {
        return ChatRequestDto.builder()
                .model("qwen3:0.6b")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Hello")
                                .build()
                ))
                .stream(true)
                .think(true)
                .build();
    }

    public static ChatRequestDto invalidChatRequest() {
        return ChatRequestDto.builder()
                .model("")
                .messages(List.of())
                .build();
    }

    public static ChatRequestDto validToolChatRequest() {
        OllamaToolDefinitionDto toolDefinition = OllamaToolDefinitionDto.builder()
                .function(OllamaToolFunctionDto.builder()
                        .name("get_product_snapshot")
                        .description("Return catalog metadata for a product")
                        .parameters(OllamaToolParametersDto.builder()
                                .type("object")
                                .properties(Map.of(
                                        "productId", OllamaToolSchemaPropertyDto.builder()
                                                .type("integer")
                                                .description("Numeric product id")
                                                .build(),
                                        "name", OllamaToolSchemaPropertyDto.builder()
                                                .type("string")
                                                .description("Product name")
                                                .build()
                                ))
                                .required(List.of("name"))
                                .build())
                        .build())
                .build();

        return ChatRequestDto.builder()
                .model("qwen3:0.6b")
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("system")
                                .content("You help shoppers with real catalog data.")
                                .build(),
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Tell me the price of the iPhone 13 Pro.")
                                .build()
                ))
                .tools(List.of(toolDefinition))
                .stream(true)
                .build();
    }

}
