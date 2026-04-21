package com.awesome.testing.service.ollama.function;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.dto.ollama.ToolCallFunctionDto;
import com.awesome.testing.dto.product.ProductListDto;
import com.awesome.testing.dto.product.ProductSummaryDto;
import com.awesome.testing.service.ProductService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductCatalogFunctionHandlerTest {

    private final ProductService productService = mock(ProductService.class);
    private final ProductCatalogFunctionHandler handler =
            new ProductCatalogFunctionHandler(productService, new ObjectMapper());

    @Test
    void shouldReturnCatalogPage() {
        ProductListDto listDto = ProductListDto.builder()
                .products(List.of(ProductSummaryDto.builder()
                        .id(1L)
                        .name("Laptop")
                        .build()))
                .total(1)
                .page(0)
                .size(25)
                .build();
        when(productService.listProducts(0, 25, null, null)).thenReturn(listDto);

        ToolCallDto toolCall = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder()
                        .name("list_products")
                        .arguments(Map.of())
                        .build())
                .build();

        ChatMessageDto result = handler.handle(toolCall);

        assertThat(result.getRole()).isEqualTo("tool");
        assertThat(result.getToolName()).isEqualTo("list_products");
        assertThat(result.getContent()).contains("\"total\":1");
        assertThat(result.getContent()).contains("Laptop");
    }

    @Test
    void shouldParseOptionalArgumentsAndPassThemToProductService() {
        ProductListDto listDto = ProductListDto.builder()
                .products(List.of())
                .total(0)
                .page(2)
                .size(10)
                .build();
        when(productService.listProducts(5, 10, "phones", true)).thenReturn(listDto);

        ToolCallDto toolCall = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder()
                        .name("list_products")
                        .arguments(Map.of(
                                "offset", "5",
                                "limit", 10,
                                "category", " phones ",
                                "inStockOnly", "true"
                        ))
                        .build())
                .build();

        ChatMessageDto result = handler.handle(toolCall);

        assertThat(result.getRole()).isEqualTo("tool");
        assertThat(result.getContent()).contains("\"total\":0");
        verify(productService).listProducts(5, 10, "phones", true);
    }

    @Test
    void shouldTreatBlankCategoryAndMissingBooleanAsNull() {
        ProductListDto listDto = ProductListDto.builder()
                .products(List.of())
                .total(0)
                .page(0)
                .size(25)
                .build();
        when(productService.listProducts(0, 25, null, null)).thenReturn(listDto);

        ToolCallDto toolCall = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder()
                        .name("list_products")
                        .arguments(Map.of("category", "   "))
                        .build())
                .build();

        ChatMessageDto result = handler.handle(toolCall);

        assertThat(result.getContent()).contains("\"total\":0");
        verify(productService).listProducts(0, 25, null, null);
    }

    @Test
    void shouldReturnToolErrorForInvalidNumberArgument() {
        ToolCallDto toolCall = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder()
                        .name("list_products")
                        .arguments(Map.of("limit", "many"))
                        .build())
                .build();

        ChatMessageDto result = handler.handle(toolCall);

        assertThat(result.getRole()).isEqualTo("tool");
        assertThat(result.getToolName()).isEqualTo("list_products");
        assertThat(result.getContent()).contains("limit/offset must be numbers");
    }

    @Test
    void shouldReturnToolErrorForInvalidBooleanArgument() {
        ToolCallDto toolCall = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder()
                        .name("list_products")
                        .arguments(Map.of("inStockOnly", 1))
                        .build())
                .build();

        ChatMessageDto result = handler.handle(toolCall);

        assertThat(result.getContent()).contains("inStockOnly must be a boolean");
    }

    @Test
    void shouldReturnInternalToolErrorWhenProductServiceFails() {
        when(productService.listProducts(0, 25, null, null)).thenThrow(new RuntimeException("database down"));
        ToolCallDto toolCall = ToolCallDto.builder()
                .function(ToolCallFunctionDto.builder()
                        .name("list_products")
                        .arguments(Map.of())
                        .build())
                .build();

        ChatMessageDto result = handler.handle(toolCall);

        assertThat(result.getContent()).contains("Internal error while listing products");
    }
}
