package com.awesome.testing.service.ollama.function;

import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.dto.ollama.ToolCallFunctionDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSnapshotFunctionHandler implements FunctionCallHandler {

    static final String TOOL_NAME = "get_product_snapshot";

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ChatMessageDto handle(ToolCallDto toolCall) {
        try {
            ToolCallFunctionDto function = toolCall.getFunction();
            Map<String, Object> arguments = function.getArguments();
            log.info("Executing {} with arguments: {}", TOOL_NAME, arguments);
            ProductDto product = resolveProduct(arguments);
            log.info("Resolved product snapshot for {}", product.getName());
            return buildToolMessage(product);
        } catch (ProductNotFoundException ex) {
            log.warn("Product lookup failed: {}", ex.getMessage());
            return buildErrorMessage("Product not found");
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid arguments for {}: {}", TOOL_NAME, ex.getMessage());
            return buildErrorMessage(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error executing {}: {}", TOOL_NAME, ex.getMessage(), ex);
            return buildErrorMessage("Internal error while executing function");
        }
    }

    private ProductDto resolveProduct(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            throw new IllegalArgumentException("Either productId or name must be provided");
        }
        Long productId = extractProductId(arguments.get("productId"));
        if (productId != null) {
            log.info("Fetching product snapshot by id={}", productId);
            return productService.getProductById(productId);
        }
        String name = Optional.ofNullable(arguments.get("name"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("name must be provided when productId is absent"));
        log.info("Fetching product snapshot by name='{}'", name);
        return productService.getProductByName(name);
    }

    private Long extractProductId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("productId must be a number");
            }
        }
        throw new IllegalArgumentException("Unsupported productId type: " + value.getClass().getSimpleName());
    }

    private ChatMessageDto buildToolMessage(Object payload) throws JsonProcessingException {
        return ChatMessageDto.builder()
                .role("tool")
                .toolName(TOOL_NAME)
                .content(objectMapper.writeValueAsString(payload))
                .build();
    }

    private ChatMessageDto buildErrorMessage(String message) {
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
