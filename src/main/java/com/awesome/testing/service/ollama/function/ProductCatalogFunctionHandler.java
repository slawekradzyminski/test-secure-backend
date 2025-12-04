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
            int page = parseInt(args != null ? args.get("offset") : null, 0);
            int size = parseInt(args != null ? args.get("limit") : null, 25);
            ProductListDto list = productService.listProducts(page, size);
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
