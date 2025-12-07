package com.awesome.testing.service.ollama.function;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.service.ProductService;
import com.awesome.testing.dto.product.ProductListDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCatalogFunctionHandler implements FunctionCallHandler {

    static final String TOOL_NAME = "list_products";

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ChatMessageDto handle(ToolCallDto toolCall) {
        try {
            Map<String, Object> args = toolCall.getFunction().getArguments();
            int offset = parseInt(args != null ? args.get("offset") : null, 0);
            int limit = parseInt(args != null ? args.get("limit") : null, 25);
            String category = parseCategory(args != null ? args.get("category") : null);
            Boolean inStockOnly = parseBoolean(args != null ? args.get("inStockOnly") : null);

            ProductListDto list = productService.listProducts(offset, limit, category, inStockOnly);
            return ChatMessageDto.builder()
                    .role("tool")
                    .toolName(TOOL_NAME)
                    .content(objectMapper.writeValueAsString(list))
                    .build();
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid list_products arguments: {}", ex.getMessage());
            return errorMessage(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error executing list_products", ex);
            return errorMessage("Internal error while listing products");
        }
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("limit/offset must be numbers");
            }
        }
        throw new IllegalArgumentException("Unsupported number format");
    }

    private String parseCategory(Object value) {
        if (value == null) {
            return null;
        }
        String category = value.toString().trim();
        return category.isBlank() ? null : category;
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str && !str.isBlank()) {
            return Boolean.parseBoolean(str.trim());
        }
        throw new IllegalArgumentException("inStockOnly must be a boolean");
    }

    private ChatMessageDto errorMessage(String message) {
        try {
            return ChatMessageDto.builder()
                    .role("tool")
                    .toolName(TOOL_NAME)
                    .content(objectMapper.writeValueAsString(Map.of("error", message)))
                    .build();
        } catch (JsonProcessingException e) {
            return ChatMessageDto.builder()
                    .role("tool")
                    .toolName(TOOL_NAME)
                    .content("{\"error\":\"" + message + "\"}")
                    .build();
        }
    }
}
