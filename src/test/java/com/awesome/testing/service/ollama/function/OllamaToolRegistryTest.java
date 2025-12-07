package com.awesome.testing.service.ollama.function;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.dto.ollama.ToolCallFunctionDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaToolRegistryTest {

    @Test
    void shouldRouteToolCallToMatchingHandler() {
        FunctionCallHandler handler = mock(FunctionCallHandler.class);
        when(handler.getName()).thenReturn("demo_tool");
        ChatMessageDto expected = ChatMessageDto.builder().role("tool").toolName("demo_tool").content("{}").build();
        when(handler.handle(any())).thenReturn(expected);
        OllamaToolRegistry registry = new OllamaToolRegistry(List.of(handler));
        ToolCallDto call = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder()
                        .name("demo_tool")
                        .arguments(Map.of("foo", "bar"))
                        .build())
                .build();

        ChatMessageDto result = registry.execute(call);

        assertThat(result).isSameAs(expected);
        verify(handler).handle(call);
    }

    @Test
    void shouldFailWhenToolNotRegistered() {
        OllamaToolRegistry registry = new OllamaToolRegistry(List.of());
        ToolCallDto call = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder().name("missing").build())
                .build();

        assertThatThrownBy(() -> registry.execute(call))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported tool");
    }
}
