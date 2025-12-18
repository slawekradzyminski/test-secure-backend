package com.awesome.testing.service.ollama.function;

import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.dto.ollama.ToolCallFunctionDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSnapshotFunctionHandlerTest {

    @Mock
    private ProductService productService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductSnapshotFunctionHandler handler;

    private ProductDto sampleProduct;

    @BeforeEach
    void setUp() {
        handler = new ProductSnapshotFunctionHandler(productService, objectMapper);
        sampleProduct = ProductDto.builder()
                .id(42L)
                .name("Retro Console")
                .description("Classic gaming console")
                .price(BigDecimal.valueOf(199.99))
                .stockQuantity(15)
                .category("GAMING")
                .imageUrl("https://example.com/retro.png")
                .build();
    }

    @Test
    void shouldReturnSnapshotWhenProductIdProvided() throws Exception {
        when(productService.getProductById(42L)).thenReturn(sampleProduct);

        ChatMessageDto response = handler.handle(toolCallWithArgs(Map.of("productId", 42)));

        assertThat(response.getRole()).isEqualTo("tool");
        assertThat(response.getToolName()).isEqualTo(ProductSnapshotFunctionHandler.TOOL_NAME);
        JsonNode json = objectMapper.readTree(response.getContent());
        assertThat(json.get("name").asText()).isEqualTo("Retro Console");
        assertThat(json.get("price").asDouble()).isEqualTo(199.99);
    }

    @Test
    void shouldReturnSnapshotWhenNameProvided() throws Exception {
        when(productService.getProductByName("Retro Console")).thenReturn(sampleProduct);

        ChatMessageDto response = handler.handle(toolCallWithArgs(Map.of("name", "Retro Console")));

        JsonNode json = objectMapper.readTree(response.getContent());
        assertThat(json.get("id").asLong()).isEqualTo(42L);
    }

    @Test
    void shouldReturnErrorWhenProductNotFound() {
        when(productService.getProductById(999L)).thenThrow(new ProductNotFoundException("missing"));

        ChatMessageDto response = handler.handle(toolCallWithArgs(Map.of("productId", 999)));

        assertThat(response.getContent()).contains("error");
        assertThat(response.getContent()).contains("Product not found");
    }

    @Test
    void shouldReturnErrorWhenProductIdIsInvalid() {
        ChatMessageDto response = handler.handle(toolCallWithArgs(Map.of("productId", "abc")));

        assertThat(response.getContent()).contains("error");
        assertThat(response.getContent()).contains("productId must be a number");
    }

    private ToolCallDto toolCallWithArgs(Map<String, Object> args) {
        return ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder()
                        .name(ProductSnapshotFunctionHandler.TOOL_NAME)
                        .arguments(args)
                        .build())
                .build();
    }
}
