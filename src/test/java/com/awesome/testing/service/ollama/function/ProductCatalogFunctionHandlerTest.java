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
}
