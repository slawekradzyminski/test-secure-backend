package com.awesome.testing.service.ollama.function;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.dto.ollama.ToolCallFunctionDto;
import com.awesome.testing.service.ProductService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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
    void shouldRegisterBothProductToolsUnderTheirPublicNames() {
        ProductService productService = mock(ProductService.class);
        ObjectMapper mapper = new ObjectMapper();
        OllamaToolRegistry registry = new OllamaToolRegistry(List.of(
                new ProductCatalogFunctionHandler(productService, mapper),
                new ProductSnapshotFunctionHandler(productService, mapper)));

        ToolCallDto catalogCall = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder().name("list_products").arguments(Map.of()).build())
                .build();
        ToolCallDto snapshotCall = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder().name("get_product_snapshot").arguments(Map.of()).build())
                .build();

        assertThat(registry.execute(catalogCall).getToolName()).isEqualTo("list_products");
        assertThat(registry.execute(snapshotCall).getToolName()).isEqualTo("get_product_snapshot");
    }

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
